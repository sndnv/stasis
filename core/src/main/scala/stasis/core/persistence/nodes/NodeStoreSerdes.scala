package stasis.core.persistence.nodes

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.routing.Node

object NodeStoreSerdes extends KeyValueBackend.Serdes[Node.Id, Node] {
  import stasis.core.api.Formats._

  override implicit def serializeKey: Node.Id => String =
    _.toString

  override implicit def deserializeKey: String => Node.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: Node => ByteString =
    node => ByteString(Json.toJson(node).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Node =
    node => Json.parse(node.decodeString(StandardCharsets.UTF_8)).as[Node]
}
