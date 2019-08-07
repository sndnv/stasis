package stasis.test.specs.unit.identity.model.tokens

import java.time.Instant

import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.{RefreshTokenStoreSerdes, StoredRefreshToken}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class RefreshTokenStoreSerdesSpec extends UnitSpec {
  "RefreshTokenStoreSerdes" should "serialize and deserialize keys" in {
    val token = Generators.generateRefreshToken

    val serialized = RefreshTokenStoreSerdes.serializeKey(token)
    val deserialized = RefreshTokenStoreSerdes.deserializeKey(serialized)

    deserialized should be(token)
  }

  they should "serialize and deserialize values" in {
    val token = StoredRefreshToken(
      token = Generators.generateRefreshToken,
      client = Client.generateId(),
      owner = Generators.generateResourceOwner,
      scope = Some(Generators.generateString(withSize = 16)),
      expiration = Instant.now()
    )

    val serialized = RefreshTokenStoreSerdes.serializeValue(token)
    val deserialized = RefreshTokenStoreSerdes.deserializeValue(serialized)

    deserialized should be(token)
  }
}
