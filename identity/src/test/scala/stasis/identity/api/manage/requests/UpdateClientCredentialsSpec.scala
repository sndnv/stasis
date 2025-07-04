package stasis.identity.api.manage.requests

import scala.concurrent.duration._

import stasis.identity.model.secrets.Secret
import io.github.sndnv.layers.testing.UnitSpec

class UpdateClientCredentialsSpec extends UnitSpec {
  private val request = UpdateClientCredentials(
    rawSecret = "some-secret"
  )

  "An UpdateClientCredentials request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(rawSecret = "")
  }

  it should "be convertible to a Secret" in withRetry {
    implicit val config: Secret.ClientConfig = Secret.ClientConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 3.seconds
    )

    val (actualSecret, salt) = request.toSecret()
    val expectedSecret = Secret.derive(request.rawSecret, salt)

    actualSecret should be(expectedSecret)
  }
}
