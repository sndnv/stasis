package stasis.core.discovery

import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress

sealed trait ServiceApiEndpoint {
  def id: String
}

object ServiceApiEndpoint {
  final case class Api(uri: String) extends ServiceApiEndpoint {
    override lazy val id: String = s"api__$uri"
  }

  final case class Core(address: EndpointAddress) extends ServiceApiEndpoint {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override lazy val id: String = address match {
      case HttpEndpointAddress(uri)           => s"core_http__${uri.toString}"
      case GrpcEndpointAddress(host, port, _) => s"core_grpc__$host:${port.toString}"
      case other => throw new IllegalArgumentException(s"Unexpected address provided: [${other.getClass.getSimpleName}]")
    }
  }

  final case class Discovery(uri: String) extends ServiceApiEndpoint {
    override lazy val id: String = s"discovery__$uri"
  }
}
