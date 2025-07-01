package stasis.identity.model.clients

import org.apache.pekko.util.ByteString

import stasis.identity.model.Generators
import stasis.identity.model.Seconds
import stasis.identity.model.secrets.Secret
import io.github.sndnv.layers.testing.UnitSpec

class ClientSpec extends UnitSpec {
  "A Client" should "validate its fields" in {
    val client = Generators.generateClient

    an[IllegalArgumentException] should be thrownBy client.copy(redirectUri = "")
    an[IllegalArgumentException] should be thrownBy client.copy(tokenExpiration = Seconds(0))
    an[IllegalArgumentException] should be thrownBy client.copy(tokenExpiration = Seconds(-1))
    an[IllegalArgumentException] should be thrownBy client.copy(secret = Secret(value = ByteString.empty))
    an[IllegalArgumentException] should be thrownBy client.copy(salt = "")
    an[IllegalArgumentException] should be thrownBy client.copy(subject = Some(""))
  }
}
