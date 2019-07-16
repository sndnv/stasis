package stasis.test.specs.unit.identity.model.tokens

import java.time.Instant

import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.{RefreshTokenStoreSerdes, StoredRefreshToken}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class RefreshTokenStoreSerdesSpec extends UnitSpec {
  "RefreshTokenStoreSerdes" should "serialize and deserialize keys" in {
    val client = Client.generateId()

    val serialized = RefreshTokenStoreSerdes.serializeKey(client)
    val deserialized = RefreshTokenStoreSerdes.deserializeKey(serialized)

    deserialized should be(client)
  }

  they should "serialize and deserialize values" in {
    val token = StoredRefreshToken(
      token = Generators.generateRefreshToken,
      owner = Generators.generateResourceOwner,
      scope = Some(Generators.generateString(withSize = 16)),
      expiration = Instant.now()
    )

    val serialized = RefreshTokenStoreSerdes.serializeValue(token)
    val deserialized = RefreshTokenStoreSerdes.deserializeValue(serialized)

    deserialized should be(token)
  }
}
