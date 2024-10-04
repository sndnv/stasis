package stasis.test.specs.unit.core.networking.mocks

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.grpc.GrpcEndpointClient
import stasis.core.packaging
import stasis.core.packaging.Crate

class MockGrpcEndpointClient()(implicit system: ActorSystem[Nothing])
    extends GrpcEndpointClient(
      (_: GrpcEndpointAddress) => Future.failed(new RuntimeException("No credentials available")),
      context = None,
      maxChunkSize = 100
    ) {

  override def push(
    address: GrpcEndpointAddress,
    manifest: packaging.Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    Future.failed(new IllegalStateException("Mock gRPC endpoint is not available"))

  override def push(
    address: GrpcEndpointAddress,
    manifest: packaging.Manifest
  ): Future[Sink[ByteString, Future[Done]]] =
    Future.failed(new IllegalStateException("Mock gRPC endpoint is not available"))

  override def pull(
    address: GrpcEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] =
    Future.failed(new IllegalStateException("Mock gRPC endpoint is not available"))

  override def discard(
    address: GrpcEndpointAddress,
    crate: Crate.Id
  ): Future[Boolean] =
    Future.failed(new IllegalStateException("Mock gRPC endpoint is not available"))
}
