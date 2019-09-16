package stasis.test.specs.unit.core.networking.grpc.internal

import java.util.UUID

import akka.util.ByteString
import stasis.core.networking.exceptions.EndpointFailure
import stasis.core.networking.grpc.internal.Implicits
import stasis.core.networking.grpc.proto
import stasis.test.specs.unit.UnitSpec

class ImplicitsSpec extends UnitSpec {
  it should "convert UUIDs" in {
    val javaUuid: UUID = UUID.randomUUID()

    val protoUuid: proto.Uuid = proto.Uuid(
      mostSignificantBits = javaUuid.getMostSignificantBits,
      leastSignificantBits = javaUuid.getLeastSignificantBits
    )

    Implicits.protoToJavaUuid(protoUuid) should be(javaUuid)
    Implicits.javaToProtoUuid(javaUuid) should be(protoUuid)
  }

  it should "convert failures" in {
    val throwable = EndpointFailure("test failure")
    val failure = proto.Failure(throwable.getMessage)

    Implicits.throwableToFailure(throwable) should be(failure)
    Implicits.failureToThrowable(failure) should be(throwable)
  }

  it should "convert optional failures" in {
    val throwable = EndpointFailure("test failure")
    val failure = proto.Failure(throwable.getMessage)

    Implicits.optionalFailureToThrowable(Some(failure)) should be(throwable)
    Implicits.optionalFailureToThrowable(None) should be(EndpointFailure("Failure message missing"))
  }

  it should "convert byte strings" in {
    val akkaByteString = ByteString.fromString("test string")
    val protoByteString = com.google.protobuf.ByteString.copyFrom(akkaByteString.asByteBuffer)

    Implicits.akkaToProtobufByteString(akkaByteString) should be(protoByteString)
    Implicits.protobufToAkkaByteString(protoByteString) should be(akkaByteString)
  }
}
