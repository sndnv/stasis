package stasis.client.service.components.maintenance

import java.io.FileNotFoundException
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.util.ByteString
import stasis.client.api.clients.{DefaultServerApiEndpointClient, ServerApiEndpointClient}
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.UserPassword
import stasis.client.security.{CredentialsProvider, DefaultCredentialsProvider}
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.core.security.oauth.{DefaultOAuthClient, OAuthClient}
import stasis.core.security.tls.EndpointContext
import stasis.shared.secrets.SecretsConfig

trait Secrets {
  def apply(): Future[Done]
}

object Secrets {
  def apply(base: Base, init: Init): Future[Secrets] = {
    import base._

    base.args.deviceSecretOperation match {
      case None =>
        Future.successful(
          new Secrets {
            override def apply(): Future[Done] = {
              log.debug("No secrets operation requested; skipping...")
              Future.successful(Done)
            }
          }
        )

      case Some(deviceSecretOperation) =>
        val coreScope = Try(rawConfig.getString("server.authentication.scopes.core")).toOption
        val apiScope = Try(rawConfig.getString("server.authentication.scopes.api")).toOption

        val tokenAndOauthClient = Try {
          val tokenEndpoint = rawConfig.getString("server.authentication.token-endpoint")

          val client = new DefaultOAuthClient(
            tokenEndpoint = tokenEndpoint,
            client = rawConfig.getString("server.authentication.client-id"),
            clientSecret = rawConfig.getString("server.authentication.client-secret"),
            useQueryString = rawConfig.getBoolean("server.authentication.use-query-string"),
            context = EndpointContext(rawConfig.getConfig("server.authentication.context"))
          )

          (tokenEndpoint, client)
        }

        for {
          secretsConfig <- SecretsConfig(config = rawConfig.getConfig("secrets"), ivSize = Aes.IvSize).future
          user <- UUID.fromString(rawConfig.getString("server.api.user")).future
          userSalt <- rawConfig.getString("server.api.user-salt").future
          device <- UUID.fromString(rawConfig.getString("server.api.device")).future
          (username, password) <- init.credentials().transformFailureTo(ServiceStartupFailure.credentials)
          userPassword = UserPassword(user = user, salt = userSalt, password = password)(secretsConfig)
          _ = log.debug("Loading encrypted device secret from [{}]...", Files.DeviceSecret)
          encryptedDeviceSecret <- directory
            .pullFile[ByteString](file = Files.DeviceSecret)
            .map(Some.apply)
            .recover { case _: FileNotFoundException => None }
            .transformFailureTo(ServiceStartupFailure.file)
          hashedEncryptionPassword = userPassword.toHashedEncryptionPassword
          keyStoreEncryptionSecret = hashedEncryptionPassword.toKeyStoreEncryptionSecret
          localEncryptionSecret = hashedEncryptionPassword.toLocalEncryptionSecret
          decryptedDeviceSecret <- encryptedDeviceSecret match {
            case Some(encryptedSecret) =>
              log.error("Decrypting device secret...")
              localEncryptionSecret
                .decryptDeviceSecret(device = device, encryptedSecret = encryptedSecret)
                .map(Some.apply)
                .transformFailureTo(ServiceStartupFailure.credentials)
            case None =>
              log.debug("Encrypted device secret not found; skipping decryption...")
              Future.successful(None)
          }
          (tokenEndpoint, oauthClient) <- tokenAndOauthClient.future
          _ = log.debug("Retrieving core token from [{}] with scope [{}]...", tokenEndpoint, coreScope)
          coreToken <- oauthClient
            .token(
              scope = coreScope,
              parameters = OAuthClient.GrantParameters.ClientCredentials()
            )
            .transformFailureTo(ServiceStartupFailure.token)
          _ = log.debug("Retrieving API token from [{}] with scope [{}]...", tokenEndpoint, apiScope)
          apiToken <- oauthClient
            .token(
              scope = apiScope,
              parameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
                username = username,
                password = userPassword.toAuthenticationPassword.extract()
              )
            )
            .transformFailureTo(ServiceStartupFailure.token)
        } yield {
          val credentialsProvider: CredentialsProvider =
            DefaultCredentialsProvider(
              tokens = DefaultCredentialsProvider.Tokens(
                core = coreToken,
                api = apiToken,
                expirationTolerance = rawConfig.getDuration("server.authentication.expiration-tolerance").toMillis.millis
              ),
              client = oauthClient
            )

          val apiClient: ServerApiEndpointClient = DefaultServerApiEndpointClient(
            apiUrl = rawConfig.getString("server.api.url"),
            credentials = credentialsProvider.api,
            decryption = DefaultServerApiEndpointClient.DecryptionContext.Disabled,
            self = device,
            context = EndpointContext(rawConfig.getConfig("server.api.context")),
            requestBufferSize = rawConfig.getInt("server.api.request-buffer-size")
          )

          def push(): Future[Done] =
            decryptedDeviceSecret match {
              case Some(secret) =>
                for {
                  encryptedDeviceSecret <- keyStoreEncryptionSecret.encryptDeviceSecret(secret = secret)
                  _ <- apiClient.pushDeviceKey(encryptedDeviceSecret).transformFailureTo(ServiceStartupFailure.api)
                } yield {
                  Done
                }

              case None =>
                Future.failed(
                  ServiceStartupFailure.credentials(
                    e = new RuntimeException("Failed to push device secret; no secret was found locally")
                  )
                )
            }

          def pull(): Future[Done] = for {
            keyResponse <- apiClient.pullDeviceKey().transformFailureTo(ServiceStartupFailure.api)
            decryptedDeviceSecret <- keyResponse match {
              case Some(encryptedDeviceSecret) =>
                keyStoreEncryptionSecret
                  .decryptDeviceSecret(device = device, encryptedSecret = encryptedDeviceSecret)
                  .transformFailureTo(ServiceStartupFailure.credentials)

              case None =>
                Future.failed(
                  ServiceStartupFailure.credentials(
                    e = new RuntimeException("Failed to pull device secret; no secret was found on server")
                  )
                )
            }
            encryptedDeviceSecret <- localEncryptionSecret.encryptDeviceSecret(secret = decryptedDeviceSecret)
            _ = log.infoN("Storing encrypted device secret to [{}]...", Files.DeviceSecret)
            _ <- directory
              .pushFile[ByteString](file = Files.DeviceSecret, content = encryptedDeviceSecret)
              .transformFailureTo(ServiceStartupFailure.file)
          } yield {
            Done
          }

          new Secrets {
            override def apply(): Future[Done] =
              deviceSecretOperation match {
                case ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push =>
                  log.debug("Pushing device secret to [{}]...", apiClient.server)
                  push()

                case ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull =>
                  log.debug("Pulling device secret from [{}]...", apiClient.server)
                  pull()
              }
          }
        }
    }
  }
}
