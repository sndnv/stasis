package stasis.core.networking.grpc

import stasis.core.networking.EndpointAddress

final case class GrpcEndpointAddress(host: String, port: Int, tlsEnabled: Boolean) extends EndpointAddress
