package stasis.identity.api.manage.requests

import scala.concurrent.duration._

import stasis.identity.model.Generators
import stasis.identity.model.secrets.Secret
import io.github.sndnv.layers.testing.UnitSpec

class CreateClientSpec extends UnitSpec {
  private val request = CreateClient(
    redirectUri = "some-uri",
    tokenExpiration = 5.seconds,
    rawSecret = "some-secret",
    subject = None
  )

  "A CreateClient request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(redirectUri = "")
    an[IllegalArgumentException] should be thrownBy request.copy(tokenExpiration = 0.seconds)
    an[IllegalArgumentException] should be thrownBy request.copy(rawSecret = "")
    an[IllegalArgumentException] should be thrownBy request.copy(subject = Some(""))
  }

  it should "be convertible to Client" in withRetry {
    implicit val config: Secret.ClientConfig = Secret.ClientConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 3.seconds
    )

    val expectedClient = Generators.generateClient.copy(
      redirectUri = request.redirectUri,
      tokenExpiration = request.tokenExpiration,
      subject = request.subject
    )

    val actualClient = request.toClient.copy(created = expectedClient.created, updated = expectedClient.updated)

    actualClient should be(
      expectedClient.copy(id = actualClient.id, secret = actualClient.secret, salt = actualClient.salt)
    )
  }
}
