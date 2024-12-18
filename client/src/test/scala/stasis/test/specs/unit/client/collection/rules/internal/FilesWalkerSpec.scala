package stasis.test.specs.unit.client.collection.rules.internal

import stasis.client.collection.rules.internal.FilesWalker
import stasis.layers.FileSystemHelpers.FileSystemSetup
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class FilesWalkerSpec extends UnitSpec with ResourceHelpers with FilesWalkerBehaviour {
  "A FilesWalker FilterResult" should "support checking if a result is empty" in {
    val empty = FilesWalker.FilterResult(matches = Map.empty, failures = Map.empty)

    empty.isEmpty should be(true)
  }

  "A FilesWalker on a Unix filesystem" should behave like walker(setup = FileSystemSetup.Unix)

  "A FilesWalker on a MacOS filesystem" should behave like walker(setup = FileSystemSetup.MacOS)

  "A FilesWalker on a Windows filesystem" should behave like walker(setup = FileSystemSetup.Windows)
}
