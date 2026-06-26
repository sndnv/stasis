package stasis.test.specs.unit.client.ops.integration.mocks

import scala.collection.concurrent.TrieMap

import org.apache.pekko.util.ByteString

class MockLibrary(
  val scheme: String,
  val entries: Map[String, ByteString]
) {
  val restored: TrieMap[String, ByteString] = TrieMap.empty
  val restoredAttributes: TrieMap[String, ByteString] = TrieMap.empty
}
