package stasis.core.networking.grpc.internal

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.{SingleResponseRequestBuilder, StreamResponseRequestBuilder}
import akka.http.scaladsl.model.headers.HttpCredentials
import stasis.core.networking.grpc.{proto, GrpcEndpointAddress}
import stasis.core.security.tls.EndpointContext

import scala.concurrent.ExecutionContext

private[grpc] class Client(
  address: GrpcEndpointAddress,
  context: Option[EndpointContext]
)(implicit system: ActorSystem[SpawnProtocol.Command]) {
  implicit val ec: ExecutionContext = system.executionContext

  val client: proto.StasisEndpointClient = {
    val baseSettings = GrpcClientSettings
      .connectToServiceAt(address.host, address.port)(system.classicSystem)
      .withTls(address.tlsEnabled)

    proto.StasisEndpointClient(
      settings = context match {
        case Some(context) => baseSettings.withSslContext(context.ssl)
        case None          => baseSettings
      }
    )
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
  def apply(
    address: GrpcEndpointAddress,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): Client =
    new Client(address, context)
}
