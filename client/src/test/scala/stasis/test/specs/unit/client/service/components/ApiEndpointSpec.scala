package stasis.test.specs.unit.client.service.components

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.api.clients.Clients
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.ops.commands.CommandProcessor
import stasis.client.ops.monitoring.ServerMonitor
import stasis.client.ops.scheduling.OperationExecutor
import stasis.client.ops.scheduling.OperationScheduler
import stasis.client.ops.search.Search
import stasis.client.security.CredentialsProvider
import stasis.client.service.ApplicationTray
import stasis.client.service.components._
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

class ApiEndpointSpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApiEndpoint component" should "create itself from config" in {
    val apiEndpointPort = ports.dequeue()

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$apiEndpointConfigEntry: $apiEndpointPort}"""
        )
      }
    )

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = secrets
    ).map { endpoint =>
      noException should be thrownBy endpoint.api.start().await
      java.nio.file.Files.readString(directory.config.get.resolve(Files.ApiToken)) should not be empty
    }
  }

  it should "support terminating the service after a delay" in {
    val apiEndpointPort = ports.dequeue()
    val apiTerminationDelay = 250.millis

    val terminationCounter = new AtomicInteger(0)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{
             |$apiTerminationDelayEntry: "${apiTerminationDelay.toMillis} millis",
             |$apiEndpointConfigEntry: $apiEndpointPort
             }""".stripMargin.replaceAll("\n", "")
        )
      }
    )

    val base = Base(
      applicationDirectory = directory,
      applicationTray = ApplicationTray.NoOp(),
      terminate = () => { val _ = terminationCounter.incrementAndGet() }
    ).await

    val endpoint = ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = secrets
    ).await

    val _ = endpoint.api.start().await

    val endpointToken = java.nio.file.Files.readString(directory.config.get.resolve(Files.ApiToken))

    val response = Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$apiEndpointPort/service/stop"
        ).addCredentials(credentials = OAuth2BearerToken(token = endpointToken))
      )
      .await

    response.status should be(StatusCodes.NoContent)

    await(delay = apiTerminationDelay / 2, withSystem = typedSystem)
    terminationCounter.get should be(0)

    await(delay = apiTerminationDelay, withSystem = typedSystem)
    terminationCounter.get should be(1)
  }

  it should "provide a user password verification handler via context" in {
    val apiEndpointPort = ports.dequeue()

    val passwordVerified = new AtomicBoolean(false)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$apiEndpointConfigEntry: $apiEndpointPort}"""
        )
      }
    )

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = new MockSecrets {
        override def verifyUserPassword: Array[Char] => Boolean = { password =>
          val _ = passwordVerified.set(true)
          super.verifyUserPassword(password)
        }
      }
    ).map { endpoint =>
      passwordVerified.get() should be(false)
      endpoint.context.handlers.verifyUserPassword("test-password".toCharArray)
      passwordVerified.get() should be(true)
    }
  }

  it should "provide a user credentials update handler via context" in {
    val apiEndpointPort = ports.dequeue()

    val credentialsUpdated = new AtomicBoolean(false)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$apiEndpointConfigEntry: $apiEndpointPort}"""
        )
      }
    )

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = new MockSecrets {
        override def updateUserCredentials: (ServerApiEndpointClient, Array[Char], String) => Future[Done] = {
          case (api, password, salt) =>
            val _ = credentialsUpdated.set(true)
            super.updateUserCredentials(api, password, salt)
        }
      }
    ).map { endpoint =>
      credentialsUpdated.get() should be(false)
      endpoint.context.handlers.updateUserCredentials("test-password".toCharArray, "test-salt")
      credentialsUpdated.get() should be(true)
    }
  }

  it should "provide a device secret re-encryption handler via context" in {
    val apiEndpointPort = ports.dequeue()

    val secretReEncrypted = new AtomicBoolean(false)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$apiEndpointConfigEntry: $apiEndpointPort}"""
        )
      }
    )

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = new MockSecrets {
        override def reEncryptDeviceSecret: (ServerApiEndpointClient, Array[Char]) => Future[Done] = { case (api, password) =>
          val _ = secretReEncrypted.set(true)
          super.reEncryptDeviceSecret(api, password)
        }
      }
    ).map { endpoint =>
      secretReEncrypted.get() should be(false)
      endpoint.context.handlers.reEncryptDeviceSecret("test-password".toCharArray)
      secretReEncrypted.get() should be(true)
    }
  }

  it should "handle token file write failures" in {
    val directory = createApplicationDirectory(init = _ => ())

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = secrets
    ).map { result =>
      fail(s"Unexpected result received: [$result]")
    }.recover { case NonFatal(e: ServiceStartupFailure) =>
      e.cause should be("file")
      e.message should include(s"File [${Files.ApiToken}] could not be created")
    }
  }

  it should "handle endpoint binding failures" in {
    val apiEndpointPort = 1

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$apiEndpointConfigEntry: $apiEndpointPort}"""
        )
      }
    )

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    val endpoint = ApiEndpoint(
      base = base,
      tracking = Tracking(base).await,
      apiClients = new ApiClients {
        override def clients: Clients =
          Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
        override def commandProcessor: CommandProcessor = MockCommandProcessor()
      },
      secrets = secrets
    ).await

    endpoint.api
      .start()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        e.cause should be("api")
        e.message should include("BindException")
        e.message should include("Permission denied")
      }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ApiEndpointSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val apiEndpointConfigEntry = "stasis.client.api.http.port"
  private val apiTerminationDelayEntry = "stasis.client.service.termination-delay"

  private val ports: mutable.Queue[Int] = (30000 to 30100).to(mutable.Queue)

  private val secrets = new MockSecrets()

  implicit val secretsConfig: SecretsConfig = SecretsConfig(
    config = typedSystem.settings.config.getConfig("stasis.client.secrets"),
    ivSize = Aes.IvSize
  )

  private class MockSecrets extends Secrets {
    override def deviceSecret: DeviceSecret =
      DeviceSecret(
        user = User.generateId(),
        device = Device.generateId(),
        secret = ByteString.empty
      )

    override def credentialsProvider: CredentialsProvider =
      new CredentialsProvider {
        override def core: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
        override def api: Future[HttpCredentials] = Future.successful(OAuth2BearerToken(token = "test-token"))
      }

    override def config: SecretsConfig = secretsConfig

    override def verifyUserPassword: Array[Char] => Boolean = _ => false

    override def updateUserCredentials: (ServerApiEndpointClient, Array[Char], String) => Future[Done] = (_, _, _) =>
      Future.successful(Done)

    override def reEncryptDeviceSecret: (ServerApiEndpointClient, Array[Char]) => Future[Done] = (_, _) => Future.successful(Done)
  }
}
