package stasis.core.networking.grpc.internal

import scala.concurrent.ExecutionContext

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.grpc.GrpcClientSettings
import org.apache.pekko.grpc.scaladsl.SingleResponseRequestBuilder
import org.apache.pekko.grpc.scaladsl.StreamResponseRequestBuilder
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.grpc.proto
import stasis.layers.security.tls.EndpointContext

private[grpc] class Client(
  address: GrpcEndpointAddress,
  context: Option[EndpointContext]
)(implicit system: ActorSystem[Nothing]) {
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
  )(implicit system: ActorSystem[Nothing]): Client =
    new Client(address, context)
}
