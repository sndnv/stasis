package stasis.test.specs.unit.client.service.components.bootstrap

import java.nio.file.Path

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.ByteString
import com.google.common.jimfs.Jimfs
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.components.Files
import stasis.client.service.components.bootstrap.{Base, Init, Secrets}
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.{ApplicationArguments, ApplicationDirectory}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{EncodingHelpers, ResourceHelpers}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class SecretsSpec extends AsyncUnitSpec with ResourceHelpers with EncodingHelpers {
  "A Secrets component" should "support creating new device secrets" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = userPassword
    )

    val directory = createCustomApplicationDirectory()

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      _ <- secrets.create()
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      deviceSecret.size should be >= Secrets.DefaultDeviceSecretSize
    }
  }

  it should "handle credentials retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = userPassword
    )

    val directory = createCustomApplicationDirectory()

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[Array[Char]] = Future.failed(new RuntimeException("test failure"))
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e: ServiceStartupFailure) =>
          e.cause should be("credentials")
          e.message should be("RuntimeException: test failure")
      }
  }

  it should "handle config override file retrieval failures" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = userPassword
    )

    Secrets(
      base = Base(
        modeArguments = modeArguments,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      ).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[Array[Char]] = Future.successful(userPassword)
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e: ServiceStartupFailure) =>
          e.cause should be("config")
          e.message should startWith("FileNotFoundException: File [client.conf] not found")
      }
  }

  it should "handle device secret file creation failures" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = userPassword
    )

    val filesystem = Jimfs.newFileSystem()

    val directory = new ApplicationDirectory.Default(
      applicationName = "test-app",
      filesystem = filesystem
    ) {
      override def pushFile[T](file: String, content: T)(implicit ec: ExecutionContext, m: T => ByteString): Future[Path] =
        Future.failed(new RuntimeException("test failure"))
    }

    val path = directory.config.get
    java.nio.file.Files.createDirectories(path)
    java.nio.file.Files.writeString(path.resolve(Files.ConfigOverride), "")

    Secrets(
      base = Base(modeArguments = modeArguments, applicationDirectory = directory).await,
      init = new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] = Future.successful(modeArguments)
        override def credentials(): Future[Array[Char]] = Future.successful(userPassword)
      }
    ).await
      .create()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e: ServiceStartupFailure) =>
          e.cause should be("file")
          e.message should startWith("RuntimeException: test failure")
      }
  }

  it should "skip creating device secret if one already exists" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = s"https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = userPassword
    )

    val directory = createCustomApplicationDirectory(deviceSecret = Some(encryptedDeviceSecret))

    for {
      base <- Base(modeArguments = modeArguments, applicationDirectory = directory)
      init <- Init(base, console = None)
      secrets <- Secrets(base, init)
      _ <- secrets.create()
      deviceSecret <- directory.pullFile[ByteString](Files.DeviceSecret)
    } yield {
      deviceSecret should be(encryptedDeviceSecret)
    }
  }

  private def createCustomApplicationDirectory(): ApplicationDirectory =
    createCustomApplicationDirectory(deviceSecret = None)

  private def createCustomApplicationDirectory(deviceSecret: Option[ByteString]): ApplicationDirectory =
    createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(path.resolve(Files.ConfigOverride), "")

        deviceSecret.foreach { secret =>
          java.nio.file.Files.write(path.resolve(Files.DeviceSecret), secret.toArray)
        }
      }
    )

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "InitSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val encryptedDeviceSecret = "08ko0LWDalPvEHna/WWuq3LoEaA3m4dQ1QuP".decodeFromBase64 // decrypted == "test-secret"

  private val userPassword = "test-password".toCharArray
}
