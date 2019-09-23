package stasis.core.routing

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.Logging
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.core.networking.EndpointClient
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.crates.CrateStore

import scala.concurrent.{ExecutionContext, Future}

class NodeProxy(
  val httpClient: EndpointClient[HttpEndpointAddress, _],
  val grpcClient: EndpointClient[GrpcEndpointAddress, _]
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val log = Logging(system.toUntyped, this.getClass.getName)

  private val cache: MemoryBackend[Node.Id, CrateStore] = MemoryBackend[Node.Id, CrateStore](
    name = s"crate-store-cache-${java.util.UUID.randomUUID()}"
  )

  def push(node: Node, manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.persist(manifest, content))

      case Node.Remote.Http(_, address) =>
        httpClient.push(address, manifest, content)

      case Node.Remote.Grpc(_, address) =>
        grpcClient.push(address, manifest, content)
    }

  def sink(node: Node, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.sink(manifest.crate))

      case Node.Remote.Http(_, address) =>
        httpClient.sink(address, manifest)

      case Node.Remote.Grpc(_, address) =>
        grpcClient.sink(address, manifest)
    }

  def pull(node: Node, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.retrieve(crate))

      case Node.Remote.Http(_, address) =>
        httpClient.pull(address, crate)

      case Node.Remote.Grpc(_, address) =>
        grpcClient.pull(address, crate)
    }

  def canStore(node: Node, request: CrateStorageRequest): Future[Boolean] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.canStore(request))

      case _: Node.Remote[_] =>
        log.info("Skipping reservation on node [{}]; reserving on remote nodes is not supported", node)
        Future.successful(false)
    }

  def discard(node: Node, crate: Crate.Id): Future[Boolean] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.discard(crate))

      case Node.Remote.Http(_, address) =>
        httpClient.discard(address, crate)

      case Node.Remote.Grpc(_, address) =>
        grpcClient.discard(address, crate)
    }

  def stores: Future[Map[Node.Id, CrateStore]] =
    cache.entries

  protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
    cache.get(id).flatMap {
      case Some(store) =>
        log.debug("Crate store for node [{}] retrieved from cache", id)
        Future.successful(store)

      case None =>
        log.debug("Crate store for node [{}] not found in cache; creating...", id)
        val store = CrateStore.fromDescriptor(storeDescriptor)
        cache.put(id, store).map(_ => store)
    }
}
