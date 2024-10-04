package stasis.test.specs.unit.identity.api.manage.requests

import scala.concurrent.duration._

import stasis.identity.api.manage.requests.CreateOwner
import stasis.identity.model.secrets.Secret
import stasis.layers.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class CreateOwnerSpec extends UnitSpec {
  private val request = CreateOwner(
    username = "some-username",
    rawPassword = "some-password",
    allowedScopes = Seq("some-scope"),
    subject = None
  )

  "A CreateOwner request" should "validate its content" in withRetry {
    an[IllegalArgumentException] should be thrownBy request.copy(username = "")
    an[IllegalArgumentException] should be thrownBy request.copy(rawPassword = "")
    an[IllegalArgumentException] should be thrownBy request.copy(subject = Some(""))
  }

  it should "be convertible to ResourceOwner" in withRetry {
    implicit val config: Secret.ResourceOwnerConfig = Secret.ResourceOwnerConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 3.seconds
    )

    val expectedOwner = Generators.generateResourceOwner.copy(
      username = request.username,
      allowedScopes = request.allowedScopes,
      subject = request.subject
    )

    val actualOwner = request.toResourceOwner.copy(created = expectedOwner.created, updated = expectedOwner.updated)

    actualOwner should be(expectedOwner.copy(password = actualOwner.password, salt = actualOwner.salt))
  }
}
