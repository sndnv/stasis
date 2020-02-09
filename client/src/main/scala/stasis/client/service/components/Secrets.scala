package stasis.client.service.components

import java.util.UUID

import akka.util.ByteString
import stasis.client.encryption.secrets.{DeviceSecret, Secret, UserPassword}
import stasis.client.encryption.Aes
import stasis.client.security.{CredentialsProvider, DefaultCredentialsProvider}
import stasis.client.service.CredentialsReader
import stasis.core.security.oauth.{DefaultOAuthClient, OAuthClient}
import stasis.core.security.tls.EndpointContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait Secrets {
  def deviceSecret: DeviceSecret
  def credentialsProvider: CredentialsProvider
}

object Secrets {
  def apply(base: Base, credentialsReader: CredentialsReader): Future[Secrets] = {
    import base._

    val oauthClient = Try {
      new DefaultOAuthClient(
        tokenEndpoint = rawConfig.getString("server.authentication.token-endpoint"),
        client = rawConfig.getString("server.authentication.client-id"),
        clientSecret = rawConfig.getString("server.authentication.client-secret"),
        useQueryString = rawConfig.getBoolean("server.authentication.use-query-string"),
        context = EndpointContext.fromConfig(rawConfig.getConfig("server.authentication.context"))
      )
    }

    for {
      secretsConfig <- Secret.Config(rawConfig = rawConfig.getConfig("secrets"), ivSize = Aes.IvSize).future
      user <- UUID.fromString(rawConfig.getString("server.api.user")).future
      userSalt <- rawConfig.getString("server.api.user-salt").future
      device <- UUID.fromString(rawConfig.getString("server.api.device")).future
      (username, password) <- credentialsReader.retrieve().future
      userPassword = UserPassword(
        user = user,
        salt = userSalt,
        password = password
      )(secretsConfig)
      encryptedDeviceSecret <- directory.pullFile[ByteString](file = Files.DeviceSecret)
      decryptedDeviceSecret <- userPassword.toHashedEncryptionPassword.toEncryptionSecret
        .decryptDeviceSecret(
          device = device,
          encryptedSecret = encryptedDeviceSecret
        )
      oauthClient <- oauthClient.future
      coreToken <- oauthClient.token(
        scope = Try(rawConfig.getString("server.authentication.scopes.core")).toOption,
        parameters = OAuthClient.GrantParameters.ClientCredentials()
      )
      apiToken <- oauthClient.token(
        scope = Try(rawConfig.getString("server.authentication.scopes.api")).toOption,
        parameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
          username = username,
          password = userPassword.toHashedAuthenticationPassword.extract()
        )
      )
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
