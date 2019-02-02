package stasis.core.networking.grpc

sealed trait GrpcCredentials

object GrpcCredentials {
  final case class Jwt(token: String) extends GrpcCredentials
  final case class Psk(node: String, secret: String) extends GrpcCredentials
}
