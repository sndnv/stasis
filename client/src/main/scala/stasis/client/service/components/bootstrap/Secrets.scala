package stasis.client.service.components.bootstrap

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Random, Try}

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.util.ByteString
import stasis.client.api.clients.DefaultServerApiEndpointClient
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, UserPassword}
import stasis.client.security.DefaultCredentialsProvider
import stasis.client.service.components.Files
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.internal.ConfigOverride
import stasis.core.security.oauth.{DefaultOAuthClient, OAuthClient}
import stasis.core.security.tls.EndpointContext
import stasis.shared.secrets.SecretsConfig

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
              configOverride <- ConfigOverride.require(directory).transformFailureTo(ServiceStartupFailure.config)
              rawConfig <- configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve().future
              secretsConfig <- SecretsConfig(config = rawConfig.getConfig("secrets"), ivSize = Aes.IvSize).future
              user <- UUID.fromString(rawConfig.getString("server.api.user")).future
              userSalt <- rawConfig.getString("server.api.user-salt").future
              device <- UUID.fromString(rawConfig.getString("server.api.device")).future
              (username, password) <- init.credentials().transformFailureTo(ServiceStartupFailure.credentials)
              userPassword = UserPassword(user = user, salt = userSalt, password = password)(secretsConfig)
              hashedEncryptionPassword = userPassword.toHashedEncryptionPassword
              keyStoreEncryptionSecret = hashedEncryptionPassword.toKeyStoreEncryptionSecret
              localEncryptionSecret = hashedEncryptionPassword.toLocalEncryptionSecret
              coreScope = Try(rawConfig.getString("server.authentication.scopes.core")).toOption
              apiScope = Try(rawConfig.getString("server.authentication.scopes.api")).toOption
              (tokenEndpoint, oauthClient) <- Try {
                val tokenEndpoint = rawConfig.getString("server.authentication.token-endpoint")

                val client = new DefaultOAuthClient(
                  tokenEndpoint = tokenEndpoint,
                  client = rawConfig.getString("server.authentication.client-id"),
                  clientSecret = rawConfig.getString("server.authentication.client-secret"),
                  useQueryString = rawConfig.getBoolean("server.authentication.use-query-string"),
                  context = EndpointContext(rawConfig.getConfig("server.authentication.context"))
                )

                (tokenEndpoint, client)
              }.future
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
              credentialsProvider <- DefaultCredentialsProvider(
                tokens = DefaultCredentialsProvider.Tokens(
                  core = coreToken,
                  api = apiToken,
                  expirationTolerance = rawConfig.getDuration("server.authentication.expiration-tolerance").toMillis.millis
                ),
                client = oauthClient
              ).future
              apiClient <- DefaultServerApiEndpointClient(
                apiUrl = rawConfig.getString("server.api.url"),
                credentials = credentialsProvider.api,
                decryption = DefaultServerApiEndpointClient.DecryptionContext.Disabled,
                self = device,
                context = EndpointContext(rawConfig.getConfig("server.api.context")),
                requestBufferSize = rawConfig.getInt("server.api.request-buffer-size")
              ).future
              keyResponse <- apiClient.pullDeviceKey().transformFailureTo(ServiceStartupFailure.api)
              decryptedDeviceSecret <- keyResponse match {
                case Some(encryptedDeviceSecret) =>
                  log.infoN("Found existing device secret on server")
                  keyStoreEncryptionSecret.decryptDeviceSecret(
                    device = device,
                    encryptedSecret = encryptedDeviceSecret
                  )

                case None =>
                  log.infoN("No existing secret was found on server; generating a new device secret...")
                  for {
                    raw <- generateRawDeviceSecret(secretSize = DefaultDeviceSecretSize).future
                    decrypted <- DeviceSecret(user = user, device = device, secret = raw)(secretsConfig).future
                  } yield {
                    decrypted
                  }
              }
              encryptedDeviceSecret <- localEncryptionSecret.encryptDeviceSecret(secret = decryptedDeviceSecret)
              _ = log.infoN("Storing encrypted device secret to [{}]...", Files.DeviceSecret)
              _ <- directory
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
