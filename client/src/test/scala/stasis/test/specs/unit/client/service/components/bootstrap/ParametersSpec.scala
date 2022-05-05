package stasis.test.specs.unit.client.service.components.bootstrap

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.ByteString
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import stasis.client.service.components.bootstrap.{Base, Bootstrap, Parameters}
import stasis.client.service.{components, ApplicationArguments, ApplicationDirectory}
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import stasis.shared.model.devices.{Device, DeviceBootstrapParameters}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers.FileSystemSetup
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ParametersSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Parameters component" should "support applying bootstrap parameters" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
    )

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val bootstrap = new Bootstrap {
      override def execute(): Future[DeviceBootstrapParameters] = Future.successful(testParams)
    }

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      params <- Parameters(base, bootstrap)
      _ <- params.apply()
      config <- directory.pullFile[ByteString](components.Files.ConfigOverride)
      rules <- directory.pullFile[ByteString](components.Files.Default.ClientRules)
      schedules <- directory.pullFile[ByteString](components.Files.Default.ClientSchedules)
    } yield {
      config.nonEmpty should be(true)
      rules.nonEmpty should be(true)
      schedules.nonEmpty should be(false)

      directory.findFile(components.Files.TrustStores.Authentication) should be(None)
      directory.findFile(components.Files.TrustStores.ServerApi) should be(None)
      directory.findFile(components.Files.TrustStores.ServerCore) should be(None)
    }
  }

  it should "fail if no configuration directory is available" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
    )

    val directory =
      new ApplicationDirectory.Default(
        applicationName = "test-name",
        filesystem = createMockFileSystem(FileSystemSetup.Unix)._1
      ) {
        override val configDirectory: Option[Path] = None
      }

    val bootstrap = new Bootstrap {
      override def execute(): Future[DeviceBootstrapParameters] = Future.successful(testParams)
    }

    val result = for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      params <- Parameters(base, bootstrap)
      _ <- params.apply()
    } yield {
      Done
    }

    result.failed
      .map { e =>
        e.getMessage should be("No configuration directory is available")
      }
  }

  it should "create the configuration directory if it doesn't exist" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = false,
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
    )

    val directory = ApplicationDirectory.Default(
      applicationName = "test-name",
      filesystem = createMockFileSystem(FileSystemSetup.Unix)._1
    )

    exists(directory.configDirectory) should be(false)

    val bootstrap = new Bootstrap {
      override def execute(): Future[DeviceBootstrapParameters] = Future.successful(testParams)
    }

    val result = for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      params <- Parameters(base, bootstrap)
      _ <- params.apply()
    } yield {
      Done
    }

    result
      .map { _ =>
        exists(directory.configDirectory) should be(true)
      }
  }

  it should "support creating PKCS12 trust stores" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val temporaryPassword = "test-password"

    val original = EndpointContext.loadStore(config)
    val encoded = DeviceBootstrapParameters.Context.encodeKeyStore(original, temporaryPassword, config.storeType)

    val context = DeviceBootstrapParameters.Context(
      enabled = true,
      protocol = "TLS",
      storeType = config.storeType,
      temporaryStorePassword = temporaryPassword,
      storeContent = encoded
    )

    val parent = directory.config.get
    val file = "test-file"

    Parameters.storeContext(
      context = context,
      passwordSize = Parameters.TrustStore.PasswordSize,
      parent = parent,
      file = file
    ) match {
      case Success((path, password)) =>
        path should be(s"$parent/$file.p12")
        password.length should be(Parameters.TrustStore.PasswordSize)
        directory.findFile(s"$file.p12").isDefined should be(true)

      case Failure(e) =>
        fail(e.getMessage)
    }
  }

  it should "support creating JKS trust stores" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.jks",
      storeType = "JKS",
      storePassword = "changeit"
    )

    val temporaryPassword = "test-password"

    val original = EndpointContext.loadStore(config)
    val encoded = DeviceBootstrapParameters.Context.encodeKeyStore(original, temporaryPassword, config.storeType)

    val context = DeviceBootstrapParameters.Context(
      enabled = true,
      protocol = "TLS",
      storeType = config.storeType,
      temporaryStorePassword = temporaryPassword,
      storeContent = encoded
    )

    val parent = directory.config.get
    val file = "test-file"

    Parameters.storeContext(
      context = context,
      passwordSize = Parameters.TrustStore.PasswordSize,
      parent = parent,
      file = file
    ) match {
      case Success((path, password)) =>
        path should be(s"$parent/$file.jks")
        password.length should be(Parameters.TrustStore.PasswordSize)
        directory.findFile(s"$file.jks").isDefined should be(true)

      case Failure(e) =>
        fail(e.getMessage)
    }
  }

  it should "support creating PKCS12 key stores" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val parent = directory.config.get
    val file = "test-file"

    Parameters.createKeyStore(
      commonName = "test",
      storeType = "PKCS12",
      passwordSize = Parameters.TrustStore.PasswordSize,
      parent = parent,
      file = file
    ) match {
      case Success((path, password)) =>
        path should be(s"$parent/$file.p12")
        password.length should be(Parameters.KeyStore.PasswordSize)
        val created = directory.findFile(s"$file.p12")
        created match {
          case Some(file) =>
            val expectedPermissions = ApplicationDirectory.Default.CreatedFilePermissions
            val actualPermissions = PosixFilePermissions.toString(Files.getPosixFilePermissions(file))
            actualPermissions should be(expectedPermissions)

          case None =>
            fail(s"File [$file.p12] was not found")
        }

      case Failure(e) =>
        fail(e.getMessage, e)
    }
  }

  it should "support creating JKS key stores" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val parent = directory.config.get
    val file = "test-file"

    Parameters.createKeyStore(
      commonName = "test",
      storeType = "JKS",
      passwordSize = Parameters.TrustStore.PasswordSize,
      parent = parent,
      file = file
    ) match {
      case Success((path, password)) =>
        path should be(s"$parent/$file.jks")
        password.length should be(Parameters.KeyStore.PasswordSize)
        val created = directory.findFile(s"$file.jks")
        created match {
          case Some(file) =>
            val expectedPermissions = ApplicationDirectory.Default.CreatedFilePermissions
            val actualPermissions = PosixFilePermissions.toString(Files.getPosixFilePermissions(file))
            actualPermissions should be(expectedPermissions)

          case None =>
            fail(s"File [$file.jks] was not found")
        }

      case Failure(e) =>
        fail(e.getMessage, e)
    }
  }

  it should "not create a trust store if it is not enabled" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val temporaryPassword = "test-password"

    val original = EndpointContext.loadStore(config)
    val encoded = DeviceBootstrapParameters.Context.encodeKeyStore(original, temporaryPassword, config.storeType)

    val context = DeviceBootstrapParameters.Context(
      enabled = false,
      protocol = "TLS",
      storeType = config.storeType,
      temporaryStorePassword = temporaryPassword,
      storeContent = encoded
    )

    val parent = directory.config.get
    val file = "test-file"

    Parameters.storeContext(
      context = context,
      passwordSize = Parameters.TrustStore.PasswordSize,
      parent = parent,
      file = file
    ) match {
      case Success((path, password)) =>
        path should be("")
        password should be("")
        directory.findFile(s"$file.p12").isDefined should be(false)

      case Failure(e) =>
        fail(e.getMessage)
    }
  }

  private val testParams = DeviceBootstrapParameters(
    authentication = DeviceBootstrapParameters.Authentication(
      tokenEndpoint = "http://localhost:1234",
      clientId = UUID.randomUUID().toString,
      clientSecret = "test-secret",
      useQueryString = true,
      scopes = DeviceBootstrapParameters.Scopes(
        api = "urn:stasis:identity:audience:server-api",
        core = s"urn:stasis:identity:audience:${Node.generateId().toString}"
      ),
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = User.generateId().toString,
      userSalt = "test-salt",
      device = Device.generateId().toString,
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = Node.generateId().toString,
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    secrets = Fixtures.Secrets.DefaultConfig,
    additionalConfig = Json.obj()
  )

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ParametersSpec"
  )

  private def exists(path: Option[Path]): Boolean =
    path match {
      case Some(actual) => Files.exists(actual)
      case None         => fail("Expected a path but none was found")
    }

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
