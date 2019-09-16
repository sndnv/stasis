package stasis.core.networking

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}

import scala.concurrent.Future

class EndpointClientProxy(
  httpClient: EndpointClient[HttpEndpointAddress, _],
  grpcClient: EndpointClient[GrpcEndpointAddress, _]
) {
  def push[A <: EndpointAddress](address: A, manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    address match {
      case address: HttpEndpointAddress => httpClient.push(address, manifest, content)
      case address: GrpcEndpointAddress => grpcClient.push(address, manifest, content)
    }

  def sink[A <: EndpointAddress](address: A, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    address match {
      case address: HttpEndpointAddress => httpClient.sink(address, manifest)
      case address: GrpcEndpointAddress => grpcClient.sink(address, manifest)
    }

  def pull[A <: EndpointAddress](address: A, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    address match {
      case address: HttpEndpointAddress => httpClient.pull(address, crate)
      case address: GrpcEndpointAddress => grpcClient.pull(address, crate)
    }

  def discard[A <: EndpointAddress](address: A, crate: Crate.Id): Future[Boolean] =
    address match {
      case address: HttpEndpointAddress => httpClient.discard(address, crate)
      case address: GrpcEndpointAddress => grpcClient.discard(address, crate)
    }
}
