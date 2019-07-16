package stasis.test.specs.unit.identity.model.clients

import stasis.identity.model.clients.{Client, ClientStoreSerdes}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class ClientStoreSerdesSpec extends UnitSpec {
  "ClientStoreSerdes" should "serialize and deserialize keys" in {
    val client = Client.generateId()

    val serialized = ClientStoreSerdes.serializeKey(client)
    val deserialized = ClientStoreSerdes.deserializeKey(serialized)

    deserialized should be(client)
  }

  they should "serialize and deserialize values" in {
    val client = Generators.generateClient

    val serialized = ClientStoreSerdes.serializeValue(client)
    val deserialized = ClientStoreSerdes.deserializeValue(serialized)

    deserialized should be(client)
  }
}
