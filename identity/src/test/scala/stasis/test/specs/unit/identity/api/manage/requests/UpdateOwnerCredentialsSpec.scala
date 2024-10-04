package stasis.test.specs.unit.identity.api.manage.requests

import scala.concurrent.duration._

import stasis.identity.api.manage.requests.UpdateOwnerCredentials
import stasis.identity.model.secrets.Secret
import stasis.layers.UnitSpec

class UpdateOwnerCredentialsSpec extends UnitSpec {
  private val request = UpdateOwnerCredentials(
    rawPassword = "some-password"
  )

  "An UpdateOwnerCredentials request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(rawPassword = "")
  }

  it should "be convertible to a Secret" in withRetry {
    implicit val config: Secret.ResourceOwnerConfig = Secret.ResourceOwnerConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 3.seconds
    )

    val (actualSecret, salt) = request.toSecret()
    val expectedSecret = Secret.derive(request.rawPassword, salt)

    actualSecret should be(expectedSecret)
  }
}
