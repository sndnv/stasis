package stasis.core.networking.grpc.internal

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.{SingleResponseRequestBuilder, StreamResponseRequestBuilder}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.ActorMaterializer
import stasis.core.networking.grpc.{proto, GrpcEndpointAddress}

import scala.concurrent.ExecutionContext

private[grpc] class Client(
  address: GrpcEndpointAddress
)(implicit system: ActorSystem, mat: ActorMaterializer) {
  implicit val ec: ExecutionContext = system.dispatcher

  val client: proto.StasisEndpointClient = {
    val baseSettings = GrpcClientSettings
      .connectToServiceAt(address.host, address.port)
      .withTls(address.tlsEnabled)

    proto.StasisEndpointClient(baseSettings)
  }

  def requestWithCredentials[Req, Res](
    call: proto.StasisEndpointClient => SingleResponseRequestBuilder[Req, Res],
    credentials: HttpCredentials
  ): SingleResponseRequestBuilder[Req, Res] =
    call(client).addHeader(Credentials.HEADER, Credentials.marshal(credentials))

  def streamWithCredentials[Req, Res](
    call: proto.StasisEndpointClient => StreamResponseRequestBuilder[Req, Res],
    credentials: HttpCredentials
  ): StreamResponseRequestBuilder[Req, Res] =
    call(client).addHeader(Credentials.HEADER, Credentials.marshal(credentials))
}

object Client {
  def apply(address: GrpcEndpointAddress)(implicit system: ActorSystem, mat: ActorMaterializer): Client =
    new Client(address)
}
