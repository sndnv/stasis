package stasis.test.specs.unit.core.persistence

import java.util.concurrent.ThreadLocalRandom

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
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

  def generateRequest(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): CrateStorageRequest =
    CrateStorageRequest(
      crate = Crate.generateId(),
      size = rnd.nextLong(0, Long.MaxValue),
      copies = rnd.nextInt(0, Int.MaxValue),
      origin = Node.generateId(),
      source = Node.generateId()
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
    implicit system: ActorSystem[SpawnProtocol.Command],
    timeout: Timeout,
    rnd: ThreadLocalRandom = ThreadLocalRandom.current()
  ): Node.Local =
    Node.Local(
      id = Node.generateId(),
      storeDescriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
        maxSize = rnd.nextLong(0, Long.MaxValue),
        maxChunkSize = rnd.nextInt(0, Int.MaxValue),
        name = generateString(withSize = 42)
      )
    )

  def generateRemoteHttpNode(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Node.Remote.Http =
    Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress(generateUri)
    )

  def generateRemoteGrpcNode(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Node.Remote.Grpc =
    Node.Remote.Grpc(
      id = Node.generateId(),
      address = GrpcEndpointAddress(
        host = generateString(withSize = 10),
        port = rnd.nextInt(50000, 60000),
        tlsEnabled = rnd.nextBoolean()
      )
    )
}
