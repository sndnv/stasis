package stasis.core.routing

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory

import stasis.core.networking.EndpointClient
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.crates.CrateStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.telemetry.TelemetryContext

class NodeProxy(
  val httpClient: EndpointClient[HttpEndpointAddress, _],
  val grpcClient: EndpointClient[GrpcEndpointAddress, _]
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout
) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private val cache: MemoryStore[Node.Id, CrateStore] =
    MemoryStore[Node.Id, CrateStore](name = "crate-store-cache")

  def push(node: Node, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    node match {
      case Node.Local(id, storeDescriptor, _, _) =>
        crateStore(id, storeDescriptor).flatMap(_.sink(manifest.crate))

      case Node.Remote.Http(_, address, _, _, _) =>
        httpClient.push(address, manifest)

      case Node.Remote.Grpc(_, address, _, _, _) =>
        grpcClient.push(address, manifest)
    }

  def pull(node: Node, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    node match {
      case Node.Local(id, storeDescriptor, _, _) =>
        crateStore(id, storeDescriptor).flatMap(_.retrieve(crate))

      case Node.Remote.Http(_, address, _, _, _) =>
        httpClient.pull(address, crate)

      case Node.Remote.Grpc(_, address, _, _, _) =>
        grpcClient.pull(address, crate)
    }

  def canStore(node: Node, request: CrateStorageRequest): Future[Boolean] =
    node match {
      case Node.Local(id, storeDescriptor, _, _) =>
        crateStore(id, storeDescriptor).flatMap(_.canStore(request))

      case _: Node.Remote[_] =>
        log.info("Skipping reservation on node [{}]; reserving on remote nodes is not supported", node)
        Future.successful(false)
    }

  def discard(node: Node, crate: Crate.Id): Future[Boolean] =
    node match {
      case Node.Local(id, storeDescriptor, _, _) =>
        crateStore(id, storeDescriptor).flatMap(_.discard(crate))

      case Node.Remote.Http(_, address, _, _, _) =>
        httpClient.discard(address, crate)

      case Node.Remote.Grpc(_, address, _, _, _) =>
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
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): NodeProxy =
    new NodeProxy(
      httpClient = httpClient,
      grpcClient = grpcClient
    )
}
