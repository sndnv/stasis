package stasis.test.specs.unit.core.networking.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.grpc.{GrpcEndpointAddress, GrpcEndpointClient}
import stasis.core.packaging
import stasis.core.packaging.Crate

import scala.concurrent.Future

class MockGrpcEndpointClient()(implicit system: ActorSystem[SpawnProtocol.Command])
    extends GrpcEndpointClient(
      (_: GrpcEndpointAddress) => Future.failed(new RuntimeException("No credentials available")),
      context = None
    ) {
  override def push(
    address: GrpcEndpointAddress,
    manifest: packaging.Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    Future.failed(new IllegalStateException("Mock gRPC endpoint is not available"))

  override def sink(
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
