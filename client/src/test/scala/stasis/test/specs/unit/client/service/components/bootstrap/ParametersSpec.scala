package stasis.test.specs.unit.client.service.components.bootstrap

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

import stasis.client.service.ApplicationArguments
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components
import stasis.client.service.components.bootstrap.Base
import stasis.client.service.components.bootstrap.Bootstrap
import stasis.client.service.components.bootstrap.Parameters
import stasis.core.routing.Node
import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup
import io.github.sndnv.layers.security.tls.EndpointContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers

class ParametersSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Parameters component" should "support applying bootstrap parameters" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = "",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
    )

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val bootstrap = new Bootstrap {
      override def execute(): Future[DeviceBootstrapParameters] = Future.successful(testParams)
    }

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      params <- Parameters(base, bootstrap)
      beforeConfig = directory.findFile(components.Files.ConfigOverride)
      beforeRules = directory.findFile(components.Files.Default.ClientRules)
      beforeSchedules = directory.findFile(components.Files.Default.ClientSchedules)
      _ <- params.apply()
      afterConfig <- directory.pullFile[ByteString](components.Files.ConfigOverride)
      afterRules <- directory.pullFile[ByteString](components.Files.Default.ClientRules)
      afterSchedules <- directory.pullFile[ByteString](components.Files.Default.ClientSchedules)
    } yield {
      beforeConfig should be(None)
      beforeRules should be(None)
      beforeSchedules should be(None)

      afterConfig.nonEmpty should be(true)
      afterRules.nonEmpty should be(true)
      afterSchedules.nonEmpty should be(false)

      directory.findFile(components.Files.TrustStores.Authentication) should be(None)
      directory.findFile(components.Files.TrustStores.ServerApi) should be(None)
      directory.findFile(components.Files.TrustStores.ServerCore) should be(None)
    }
  }

  it should "not re-create config files if they already exist" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = "",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
    )

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val bootstrap = new Bootstrap {
      override def execute(): Future[DeviceBootstrapParameters] = Future.successful(testParams)
    }

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      params <- Parameters(base, bootstrap)
      _ <- directory.pushFile(components.Files.ConfigOverride, ByteString.fromString("test-config"))
      _ <- directory.pushFile(components.Files.Default.ClientRules, ByteString.fromString("test-rules"))
      _ <- directory.pushFile(components.Files.Default.ClientSchedules, ByteString.fromString("test-schedules"))
      beforeConfig = directory.findFile(components.Files.ConfigOverride)
      beforeRules = directory.findFile(components.Files.Default.ClientRules)
      beforeSchedules = directory.findFile(components.Files.Default.ClientSchedules)
      _ <- params.apply()
      afterConfig <- directory.pullFile[ByteString](components.Files.ConfigOverride)
      afterRules <- directory.pullFile[ByteString](components.Files.Default.ClientRules)
      afterSchedules <- directory.pullFile[ByteString](components.Files.Default.ClientSchedules)
    } yield {
      beforeConfig should not be None
      beforeRules should not be None
      beforeSchedules should not be None

      afterConfig.utf8String should be("test-config")
      afterRules.utf8String should be("test-rules")
      afterSchedules.utf8String should be("test-schedules")

      directory.findFile(components.Files.TrustStores.Authentication) should be(None)
      directory.findFile(components.Files.TrustStores.ServerApi) should be(None)
      directory.findFile(components.Files.TrustStores.ServerCore) should be(None)
    }
  }

  it should "support forcing config file creation even if they already exist" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = "",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = true
    )

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val bootstrap = new Bootstrap {
      override def execute(): Future[DeviceBootstrapParameters] = Future.successful(testParams)
    }

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      params <- Parameters(base, bootstrap)
      _ <- directory.pushFile(components.Files.ConfigOverride, ByteString.fromString("test-config"))
      _ <- directory.pushFile(components.Files.Default.ClientRules, ByteString.fromString("test-rules"))
      _ <- directory.pushFile(components.Files.Default.ClientSchedules, ByteString.fromString("test-schedules"))
      beforeConfig = directory.findFile(components.Files.ConfigOverride)
      beforeRules = directory.findFile(components.Files.Default.ClientRules)
      beforeSchedules = directory.findFile(components.Files.Default.ClientSchedules)
      _ <- params.apply()
      afterConfig <- directory.pullFile[ByteString](components.Files.ConfigOverride)
      afterRules <- directory.pullFile[ByteString](components.Files.Default.ClientRules)
      afterSchedules <- directory.pullFile[ByteString](components.Files.Default.ClientSchedules)
    } yield {
      beforeConfig should not be None
      beforeRules should not be None
      beforeSchedules should not be None

      afterConfig.nonEmpty should be(true)
      afterRules.nonEmpty should be(true)
      afterSchedules.nonEmpty should be(false)

      afterConfig.utf8String should not be "test-config"
      afterRules.utf8String should not be "test-rules"
      afterSchedules.utf8String should not be "test-schedules"

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
      userName = "",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
    )

    val directory =
      new ApplicationDirectory.Default(
        applicationName = "test-name",
        filesystem = createMockFileSystem(FileSystemSetup.Unix)._1
      ) {
        override lazy val configDirectory: Option[Path] = None
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
      userName = "",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
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

  it should "support creating key/trust store file extensions from store types" in {
    Parameters.storeTypeAsExtension(storeType = "pkcs12") should be("p12")

    Parameters.storeTypeAsExtension(storeType = "jks") should be("jks")

    an[IllegalArgumentException] should be thrownBy Parameters.storeTypeAsExtension(storeType = "other")
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
    val encoded = EndpointContext.Encoded.encodeKeyStore(original, temporaryPassword, config.storeType)

    val context = EndpointContext.Encoded(
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
    val encoded = EndpointContext.Encoded.encodeKeyStore(original, temporaryPassword, config.storeType)

    val context = EndpointContext.Encoded(
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
      name = "test",
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
      name = "test",
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
    val encoded = EndpointContext.Encoded.encodeKeyStore(original, temporaryPassword, config.storeType)

    val context = EndpointContext.Encoded(
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
      context = EndpointContext.Encoded.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = User.generateId().toString,
      userSalt = "test-salt",
      device = Device.generateId().toString,
      context = EndpointContext.Encoded.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = Node.generateId().toString,
      context = EndpointContext.Encoded.disabled()
    ),
    secrets = Fixtures.Secrets.DefaultConfig,
    additionalConfig = Json.obj()
  )

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ParametersSpec"
  )

  private def exists(path: Option[Path]): Boolean =
    path match {
      case Some(actual) => Files.exists(actual)
      case None         => fail("Expected a path but none was found")
    }

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
