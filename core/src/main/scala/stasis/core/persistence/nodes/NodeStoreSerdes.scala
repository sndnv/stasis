package stasis.core.persistence.nodes

import java.nio.charset.StandardCharsets

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.{ByteString, Timeout}
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node

class NodeStoreSerdes(
  reservationStore: ReservationStore
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout)
    extends KeyValueBackend.Serdes[Node.Id, Node] {
  import stasis.core.api.Formats._

  implicit val reservations: ReservationStore = reservationStore

  override implicit def serializeKey: Node.Id => String =
    _.toString

  override implicit def deserializeKey: String => Node.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: Node => ByteString =
    node => ByteString(Json.toJson(node).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Node =
    node => Json.parse(node.decodeString(StandardCharsets.UTF_8)).as[Node]
}
