package stasis.test.specs.unit.client.service.components.maintenance

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.util.ByteString
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.components.maintenance.{Base, Certificates}
import stasis.client.service.{components, ApplicationArguments, ApplicationDirectory}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.core.FileSystemHelpers.FileSystemSetup

import java.nio.file.{Files, Path}

class CertificatesSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Certificates component" should "support regenerating client API certificates" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val originalConfig = "stasis.client.api.http.context.keystore.password = \"test-password\""
    val originalKeyStore = ByteString("test-keystore")

    val keyStoreName = s"${components.Files.KeyStores.ClientApi}.p12"

    implicit val stringToByteString: String => ByteString = ByteString.apply
    implicit val ByteStringToString: ByteString => String = _.utf8String

    for {
      _ <- directory.pushFile(components.Files.ConfigOverride, originalConfig)
      _ <- directory.pushFile(keyStoreName, originalKeyStore)
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      certificates <- Certificates(base)
      _ <- certificates.apply()
      updatedConfig <- directory.pullFile[String](components.Files.ConfigOverride)
      updatedKeyStore <- directory.pullFile[ByteString](keyStoreName)
    } yield {
      updatedConfig should not be originalConfig
      updatedKeyStore should not be originalKeyStore
    }
  }

  it should "fail if the current API certificate password is missing" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      certificates <- Certificates(base)
      e <- certificates.apply().failed
    } yield {
      e.getMessage should include("Client API certificate regeneration failed; could not retrieve existing certificate password")
    }
  }

  it should "skip API certificate regeneration if the flag is not set" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance.Empty

    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    val originalConfig = "stasis.client.api.http.context.keystore.password = \"test-password\""
    val originalKeyStore = ByteString("test-keystore")

    val keyStoreName = s"${components.Files.KeyStores.ClientApi}.p12"

    implicit val stringToByteString: String => ByteString = ByteString.apply
    implicit val ByteStringToString: ByteString => String = _.utf8String

    for {
      _ <- directory.pushFile(components.Files.ConfigOverride, originalConfig)
      _ <- directory.pushFile(keyStoreName, originalKeyStore)
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      _ <- Certificates.regenerateApiCertificate(base)
      updatedConfig <- directory.pullFile[String](components.Files.ConfigOverride)
      updatedKeyStore <- directory.pullFile[ByteString](keyStoreName)
    } yield {
      updatedConfig should be(originalConfig)
      updatedKeyStore should be(originalKeyStore)
    }
  }

  it should "fail if no configuration directory is available" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate

    val directory =
      new ApplicationDirectory.Default(
        applicationName = "test-name",
        filesystem = createMockFileSystem(FileSystemSetup.Unix)._1
      ) {
        override lazy val configDirectory: Option[Path] = None
      }

    val result = for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      certificates <- Certificates(base)
      _ <- certificates.apply()
    } yield {
      Done
    }

    result.failed
      .map { e =>
        e.getMessage should be("No configuration directory is available")
      }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "CertificatesSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
