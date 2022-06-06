package stasis.core.routing

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import org.slf4j.LoggerFactory
import stasis.core.networking.EndpointClient
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.{ExecutionContext, Future}

class NodeProxy(
  val httpClient: EndpointClient[HttpEndpointAddress, _],
  val grpcClient: EndpointClient[GrpcEndpointAddress, _]
)(implicit
  system: ActorSystem[SpawnProtocol.Command],
  telemetry: TelemetryContext,
  timeout: Timeout
) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private val cache: MemoryBackend[Node.Id, CrateStore] = MemoryBackend[Node.Id, CrateStore](
    name = s"crate-store-cache-${java.util.UUID.randomUUID().toString}"
  )

  def push(node: Node, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.sink(manifest.crate))

      case Node.Remote.Http(_, address, _) =>
        httpClient.push(address, manifest)

      case Node.Remote.Grpc(_, address, _) =>
        grpcClient.push(address, manifest)
    }

  def pull(node: Node, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    node match {
      case Node.Local(id, storeDescriptor) =>
        crateStore(id, storeDescriptor).flatMap(_.retrieve(crate))

      case Node.Remote.Http(_, address, _) =>
        httpClient.pull(address, crate)

      case Node.Remote.Grpc(_, address, _) =>
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

      case Node.Remote.Http(_, address, _) =>
        httpClient.discard(address, crate)

      case Node.Remote.Grpc(_, address, _) =>
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

object NodeProxy {
  def apply(
    httpClient: EndpointClient[HttpEndpointAddress, _],
    grpcClient: EndpointClient[GrpcEndpointAddress, _]
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, timeout: Timeout): NodeProxy =
    new NodeProxy(
      httpClient = httpClient,
      grpcClient = grpcClient
    )
}
