package stasis.client.service.components

import org.apache.pekko.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, UserPassword}
import stasis.client.security.{CredentialsProvider, DefaultCredentialsProvider}
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.core.security.oauth.{DefaultOAuthClient, OAuthClient}
import stasis.core.security.tls.EndpointContext
import stasis.shared.secrets.SecretsConfig

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait Secrets {
  def deviceSecret: DeviceSecret
  def credentialsProvider: CredentialsProvider
}

object Secrets {
  def apply(base: Base, init: Init): Future[Secrets] = {
    import base._

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
      (username, password) <- init
        .credentials()
        .transformFailureTo(ServiceStartupFailure.credentials)
      userPassword = UserPassword(
        user = user,
        salt = userSalt,
        password = password
      )(secretsConfig)
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
      new Secrets {
        override val deviceSecret: DeviceSecret =
          decryptedDeviceSecret

        override val credentialsProvider: CredentialsProvider =
          DefaultCredentialsProvider(
            tokens = DefaultCredentialsProvider.Tokens(
              core = coreToken,
              api = apiToken,
              expirationTolerance = rawConfig.getDuration("server.authentication.expiration-tolerance").toMillis.millis
            ),
            client = oauthClient
          )
      }
    }
  }
}
