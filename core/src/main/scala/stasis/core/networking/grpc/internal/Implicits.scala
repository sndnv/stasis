package stasis.core.networking.grpc.internal

import java.util.UUID

import org.apache.pekko.util.ByteString
import stasis.core.networking.exceptions.EndpointFailure
import stasis.core.networking.grpc.proto

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

  implicit def pekkoToProtobufByteString(string: ByteString): com.google.protobuf.ByteString =
    com.google.protobuf.ByteString.copyFrom(string.asByteBuffer)

  implicit def protobufToPekkoByteString(string: com.google.protobuf.ByteString): ByteString =
    ByteString.fromByteBuffer(string.asReadOnlyByteBuffer())
}
