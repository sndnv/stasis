package stasis.identity.model.owners

import org.apache.pekko.util.ByteString

import stasis.identity.model.Generators
import stasis.identity.model.secrets.Secret
import io.github.sndnv.layers.testing.UnitSpec

class ResourceOwnerSpec extends UnitSpec {
  "A Client" should "validate its fields" in {
    val owner = Generators.generateResourceOwner

    an[IllegalArgumentException] should be thrownBy owner.copy(username = "")
    an[IllegalArgumentException] should be thrownBy owner.copy(password = Secret(value = ByteString.empty))
    an[IllegalArgumentException] should be thrownBy owner.copy(salt = "")
    an[IllegalArgumentException] should be thrownBy owner.copy(subject = Some(""))
  }
}
