package stasis.client.service.components.bootstrap

import akka.Done
import akka.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, UserPassword}
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.internal.ConfigOverride
import stasis.shared.secrets.SecretsConfig

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.Future
import scala.util.{Random, Try}

trait Secrets {
  def create(): Future[Done]
}

object Secrets {
  final val DefaultDeviceSecretSize: Int = 128

  def apply(base: Base, init: Init): Future[Secrets] = {
    import base._

    val secrets = directory.findFile(file = Files.DeviceSecret) match {
      case Some(_) =>
        new Secrets {
          override def create(): Future[Done] = {
            log.debug("Device secret [{}] already exists; skipping...", Files.DeviceSecret)
            Future.successful(Done)
          }
        }

      case None =>
        new Secrets {
          override def create(): Future[Done] =
            for {
              configOverride <-
                ConfigOverride
                  .require(directory)
                  .transformFailureTo(ServiceStartupFailure.config)
              rawConfig <- configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve().future
              secretsConfig <- SecretsConfig(config = rawConfig.getConfig("secrets"), ivSize = Aes.IvSize).future
              user <- UUID.fromString(rawConfig.getString("server.api.user")).future
              userSalt <- rawConfig.getString("server.api.user-salt").future
              device <- UUID.fromString(rawConfig.getString("server.api.device")).future
              password <-
                init
                  .credentials()
                  .transformFailureTo(ServiceStartupFailure.credentials)
              userPassword = UserPassword(
                user = user,
                salt = userSalt,
                password = password
              )(secretsConfig)
              _ = log.debug("Generating new device secret...")
              rawDeviceSecret <- generateRawDeviceSecret(secretSize = DefaultDeviceSecretSize).future
              decryptedDeviceSecret <- DeviceSecret(
                user = user,
                device = device,
                secret = rawDeviceSecret
              )(secretsConfig).future
              encryptedDeviceSecret <-
                userPassword.toHashedEncryptionPassword.toEncryptionSecret
                  .encryptDeviceSecret(secret = decryptedDeviceSecret)
              _ = log.debug("Storing encrypted device secret to [{}]...", Files.DeviceSecret)
              _ <-
                directory
                  .pushFile[ByteString](file = Files.DeviceSecret, content = encryptedDeviceSecret)
                  .transformFailureTo(ServiceStartupFailure.file)
            } yield {
              Done
            }
        }
    }

    Future.successful(secrets)
  }

  def generateRawDeviceSecret(secretSize: Int): Try[ByteString] =
    Try {
      val rnd: Random = ThreadLocalRandom.current()
      val bytes = rnd.nextBytes(n = secretSize)
      ByteString.fromArray(bytes)
    }
}
