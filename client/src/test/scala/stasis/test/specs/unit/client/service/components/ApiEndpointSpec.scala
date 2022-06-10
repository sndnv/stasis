package stasis.test.specs.unit.client.service.components

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.api.clients.Clients
import stasis.client.ops.monitoring.ServerMonitor
import stasis.client.ops.scheduling.{OperationExecutor, OperationScheduler}
import stasis.client.ops.search.Search
import stasis.client.service.components._
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

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

    ApiEndpoint(
      base = Base(applicationDirectory = directory, terminate = () => ()).await,
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
      }
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

    val endpoint = ApiEndpoint(
      base = Base(
        applicationDirectory = directory,
        terminate = () => { val _ = terminationCounter.incrementAndGet() }
      ).await,
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
      }
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

  it should "handle token file write failures" in {
    val directory = createApplicationDirectory(init = _ => ())

    ApiEndpoint(
      base = Base(applicationDirectory = directory, terminate = () => ()).await,
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
      }
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

    val endpoint = ApiEndpoint(
      base = Base(applicationDirectory = directory, terminate = () => ()).await,
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
      }
    ).await

    endpoint.api
      .start()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ServiceStartupFailure) =>
        e.cause should be("api")
        e.message should include("SocketException: Permission denied")
      }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ApiEndpointSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val apiEndpointConfigEntry = "stasis.client.api.http.port"
  private val apiTerminationDelayEntry = "stasis.client.service.termination-delay"

  private val ports: mutable.Queue[Int] = (30000 to 30100).to(mutable.Queue)
}
