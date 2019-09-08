package stasis.test.specs.unit.identity.model.codes

import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCodeStoreSerdes, StoredAuthorizationCode}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class AuthorizationCodeStoreSerdesSpec extends UnitSpec {
  "AuthorizationCodeStoreSerdes" should "serialize and deserialize keys" in {
    val code = Generators.generateAuthorizationCode

    val serialized = AuthorizationCodeStoreSerdes.serializeKey(code)
    val deserialized = AuthorizationCodeStoreSerdes.deserializeKey(serialized)

    deserialized should be(code)
  }

  they should "serialize and deserialize values" in {
    val code = StoredAuthorizationCode(
      code = Generators.generateAuthorizationCode,
      client = Client.generateId(),
      owner = Generators.generateResourceOwner,
      scope = Some(stasis.test.Generators.generateString(withSize = 16)),
      challenge = Some(
        StoredAuthorizationCode.Challenge(
          value = stasis.test.Generators.generateString(withSize = 32),
          method = Some(ChallengeMethod.S256)
        )
      )
    )

    val serialized = AuthorizationCodeStoreSerdes.serializeValue(code)
    val deserialized = AuthorizationCodeStoreSerdes.deserializeValue(serialized)

    deserialized should be(code)
  }
}
