package stasis.layers.security.mocks

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.layers.security.oauth.OAuthClient
import stasis.layers.security.oauth.OAuthClient.GrantParameters

class MockOAuthClient(token: Option[OAuthClient.AccessTokenResponse]) extends OAuthClient {
  private val clientCredentialsTokensProvidedCounter: AtomicInteger = new AtomicInteger(0)
  private val refreshTokensProvidedCounter: AtomicInteger = new AtomicInteger(0)
  private val passwordTokensProvidedCounter: AtomicInteger = new AtomicInteger(0)

  private val response = token match {
    case Some(response) => Future.successful(response)
    case None           => Future.failed(new RuntimeException("No token response is available"))
  }

  override val tokenEndpoint: String = "MockOAuthClient"

  override def token(
    scope: Option[String],
    parameters: OAuthClient.GrantParameters
  ): Future[OAuthClient.AccessTokenResponse] = {
    val _ = parameters match {
      case _: GrantParameters.ClientCredentials                => clientCredentialsTokensProvidedCounter.incrementAndGet()
      case _: GrantParameters.RefreshToken                     => refreshTokensProvidedCounter.incrementAndGet()
      case _: GrantParameters.ResourceOwnerPasswordCredentials => passwordTokensProvidedCounter.incrementAndGet()
    }

    response
  }

  def clientCredentialsTokensProvided: Int = clientCredentialsTokensProvidedCounter.get()

  def refreshTokensProvided: Int = refreshTokensProvidedCounter.get()

  def passwordTokensProvided: Int = passwordTokensProvidedCounter.get()

  def tokensProvided: Int =
    (
      clientCredentialsTokensProvidedCounter.get()
        + refreshTokensProvidedCounter.get()
        + passwordTokensProvidedCounter.get()
    )
}
