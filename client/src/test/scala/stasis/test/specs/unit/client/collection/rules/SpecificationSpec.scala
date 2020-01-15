package stasis.test.specs.unit.client.collection.rules

import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.ResourceHelpers.FileSystemSetup

class SpecificationSpec extends UnitSpec with ResourceHelpers with SpecificationBehaviour {
  "A Specification on a Unix filesystem" should behave like specification(setup = FileSystemSetup.Unix)

  "A Specification on a MacOS filesystem" should behave like specification(setup = FileSystemSetup.MacOS)

  "A Specification on a Windows filesystem" should behave like specification(setup = FileSystemSetup.Windows)
}
