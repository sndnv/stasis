package stasis.client.service.components.maintenance

import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.UserPassword
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.internal.ConfigOverride
import stasis.shared.secrets.SecretsConfig

trait Credentials {
  def apply(): Future[Done]
}

object Credentials {
  def apply(base: Base, mode: ApplicationArguments.Mode.Maintenance): Future[Credentials] = {
    import base._

    mode match {
      case operation: ApplicationArguments.Mode.Maintenance.ResetUserCredentials =>
        for {
          secretsConfig <- SecretsConfig(config = rawConfig.getConfig("secrets"), ivSize = Aes.IvSize).future
          user <- UUID.fromString(rawConfig.getString("server.api.user")).future
          userSalt <- rawConfig.getString("server.api.user-salt").future
          device <- UUID.fromString(rawConfig.getString("server.api.device")).future
          userPassword = UserPassword(user = user, salt = userSalt, password = operation.currentUserPassword)(secretsConfig)
          _ = log.debug("Loading encrypted device secret from [{}]...", Files.DeviceSecret)
          encryptedDeviceSecret <- directory
            .pullFile[ByteString](file = Files.DeviceSecret)
            .transformFailureTo(ServiceStartupFailure.file)
          _ = log.debug("Decrypting device secret...")
          decryptedDeviceSecret <- userPassword.toHashedEncryptionPassword.toLocalEncryptionSecret
            .decryptDeviceSecret(
              device = device,
              encryptedSecret = encryptedDeviceSecret
            )
            .transformFailureTo(ServiceStartupFailure.credentials)
        } yield {
          new Credentials {
            override def apply(): Future[Done] = {
              val newLocalEncryptionSecret = UserPassword(
                user = user,
                salt = operation.newUserSalt,
                password = operation.newUserPassword
              )(secretsConfig).toHashedEncryptionPassword.toLocalEncryptionSecret

              ConfigOverride.update(
                directory = directory,
                path = "stasis.client.server.api.user-salt",
                value = operation.newUserSalt
              )

              for {
                encryptedDeviceSecret <- newLocalEncryptionSecret.encryptDeviceSecret(decryptedDeviceSecret)
                _ = log.info("Storing encrypted device secret to [{}]...", Files.DeviceSecret)
                _ <- directory.pushFile[ByteString](file = Files.DeviceSecret, content = encryptedDeviceSecret)
              } yield {
                log.info("Successfully reset credentials for user [{}]", user)
                Done
              }
            }
          }
        }

      case _ =>
        Future.successful(
          new Credentials {
            override def apply(): Future[Done] = {
              log.debug("No credentials operation requested; skipping...")
              Future.successful(Done)
            }
          }
        )
    }
  }
}
