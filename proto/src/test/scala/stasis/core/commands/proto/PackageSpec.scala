package stasis.core.commands.proto

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import stasis.common.proto.Uuid

class PackageSpec extends AnyFlatSpec with Matchers {
  "An Instant mapper" should "convert Long to/from Instant" in {
    val original = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    val converted = original.toEpochMilli

    instantMapper.toBase(original) should be(converted)
    instantMapper.toCustom(converted) should be(original)
  }

  "A UUID mapper" should "convert Protobuf Uuid to/from Java UUID" in {
    val original = UUID.randomUUID()
    val converted = Uuid(original.getMostSignificantBits, original.getLeastSignificantBits)

    uuidMapper.toBase(original) should be(converted)
    uuidMapper.toCustom(converted) should be(original)
  }

  "A CommandSource mapper" should "convert String to/from CommandSource" in {
    val original = CommandSource.User
    val converted = "user"

    sourceMapper.toBase(original) should be(converted)
    sourceMapper.toCustom(converted) should be(original)
  }
}
