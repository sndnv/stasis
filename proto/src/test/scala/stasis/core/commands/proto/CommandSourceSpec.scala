package stasis.core.commands.proto

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommandSourceSpec extends AnyFlatSpec with Matchers {
  "A CommandSource" should "support converting to/from string" in {
    CommandSource.User.name should be("user")
    CommandSource.Service.name should be("service")

    CommandSource("user") should be(CommandSource.User)
    CommandSource("service") should be(CommandSource.Service)

    an[IllegalArgumentException] should be thrownBy CommandSource("other")
  }
}
