package stasis.test.specs.unit.client.collection.rules

import akka.util.Timeout
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.ResourceHelpers.FileSystemSetup

import scala.concurrent.duration._

class SpecificationSpec extends AsyncUnitSpec with ResourceHelpers with SpecificationBehaviour {
  "A Specification on a Unix filesystem" should behave like specification(setup = FileSystemSetup.Unix)

  "A Specification on a MacOS filesystem" should behave like specification(setup = FileSystemSetup.MacOS)

  "A Specification on a Windows filesystem" should behave like specification(setup = FileSystemSetup.Windows)

  override implicit val timeout: Timeout = 10.seconds
}
