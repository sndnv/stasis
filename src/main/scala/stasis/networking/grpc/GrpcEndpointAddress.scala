package stasis.networking.grpc

import stasis.networking.EndpointAddress

final case class GrpcEndpointAddress(host: String, port: Int, tlsEnabled: Boolean) extends EndpointAddress
