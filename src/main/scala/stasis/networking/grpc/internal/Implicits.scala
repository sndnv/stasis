package stasis.networking.grpc.internal

import java.util.UUID

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials, OAuth2BearerToken}
import akka.util.ByteString
import stasis.networking.exceptions.EndpointFailure
import stasis.networking.grpc.{proto, GrpcCredentials}

object Implicits {

  import scala.language.implicitConversions

  implicit def protoToJavaUuid(uuid: proto.Uuid): UUID =
    new UUID(uuid.mostSignificantBits, uuid.leastSignificantBits)

  implicit def javaToProtoUuid(uuid: UUID): proto.Uuid =
    proto.Uuid(mostSignificantBits = uuid.getMostSignificantBits, leastSignificantBits = uuid.getLeastSignificantBits)

  implicit def throwableToFailure(e: Throwable): proto.Failure =
    proto.Failure(e.getMessage)

  implicit def failureToThrowable(failure: proto.Failure): Throwable =
    EndpointFailure(failure.message)

  implicit def optionalFailureToThrowable(failure: Option[proto.Failure]): Throwable =
    failure match {
      case Some(f) => f
      case None    => EndpointFailure("Failure message missing")
    }

  implicit def akkaToProtobufByteString(string: ByteString): com.google.protobuf.ByteString =
    com.google.protobuf.ByteString.copyFrom(string.asByteBuffer)

  implicit def protobufToAkkaByteString(string: com.google.protobuf.ByteString): ByteString =
    ByteString.fromByteBuffer(string.asReadOnlyByteBuffer())

  implicit def grpcToHttpCredentials(credentials: GrpcCredentials): HttpCredentials =
    credentials match {
      case GrpcCredentials.Jwt(token)        => OAuth2BearerToken(token)
      case GrpcCredentials.Psk(node, secret) => BasicHttpCredentials(username = node, password = secret)
    }

  implicit def httpToGrpcCredentials(credentials: HttpCredentials): GrpcCredentials =
    credentials match {
      case OAuth2BearerToken(token)                 => GrpcCredentials.Jwt(token)
      case BasicHttpCredentials(username, password) => GrpcCredentials.Psk(node = username, secret = password)
    }
}
