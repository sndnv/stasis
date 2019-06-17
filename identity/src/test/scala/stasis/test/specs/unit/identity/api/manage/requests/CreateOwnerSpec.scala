package stasis.test.specs.unit.identity.api.manage.requests

import stasis.identity.api.manage.requests.CreateOwner
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class CreateOwnerSpec extends UnitSpec {
  private val request = CreateOwner(
    username = "some-username",
    rawPassword = "some-password",
    allowedScopes = Seq("some-scope")
  )

  "A CreateOwner request" should "validate its content" in {
    an[IllegalArgumentException] should be thrownBy request.copy(username = "")
    an[IllegalArgumentException] should be thrownBy request.copy(allowedScopes = Seq.empty)
    an[IllegalArgumentException] should be thrownBy request.copy(rawPassword = "")
  }

  it should "be convertible to ResourceOwner" in {
    implicit val config: Secret.ResourceOwnerConfig = Secret.ResourceOwnerConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 3.seconds
    )

    val expectedOwner = Generators.generateResourceOwner.copy(
      username = request.username,
      allowedScopes = request.allowedScopes
    )

    val actualOwner = request.toResourceOwner(realm = expectedOwner.realm)

    actualOwner should be(expectedOwner.copy(password = actualOwner.password, salt = actualOwner.salt))
  }
}
