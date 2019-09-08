package stasis.test.specs.unit.core.persistence

import java.util.concurrent.ThreadLocalRandom

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.test.Generators._

object Generators {
  def generateManifest(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Manifest =
    Manifest(
      crate = Crate.generateId(),
      size = rnd.nextLong(0, Long.MaxValue),
      copies = rnd.nextInt(0, Int.MaxValue),
      origin = Node.generateId(),
      source = Node.generateId(),
      destinations = generateSeq(g = Node.generateId())
    )

  def generateReservation(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): CrateStorageReservation =
    CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = rnd.nextLong(0, Long.MaxValue),
      copies = rnd.nextInt(0, Int.MaxValue),
      origin = Node.generateId(),
      target = Node.generateId()
    )

  def generateLocalNode(
    implicit system: ActorSystem[SpawnProtocol],
    timeout: Timeout,
    reservationStore: ReservationStore,
    rnd: ThreadLocalRandom = ThreadLocalRandom.current()
  ): Node.Local =
    Node.Local(
      id = Node.generateId(),
      crateStore = CrateStore(
        streamingBackend = StreamingMemoryBackend(
          maxSize = rnd.nextLong(0, Long.MaxValue),
          name = generateString(withSize = 42)
        ),
        reservationStore = reservationStore,
        storeId = Node.generateId()
      )(system.toUntyped)
    )

  def generateRemoteHttpNode(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Node.Remote.Http =
    Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress(generateUri)
    )
}
