package stasis.layers.files

import org.mockito.scalatest.AsyncMockitoSugar
import stasis.layers.FileSystemHelpers
import stasis.layers.FileSystemHelpers.FileSystemSetup
import stasis.layers.UnitSpec

class FilteringFileVisitorSpec extends UnitSpec with FileSystemHelpers with AsyncMockitoSugar with FilteringFileVisitorBehaviour {
  "A FilteringFileVisitor on a Unix filesystem" should behave like visitor(setup = FileSystemSetup.Unix)

  "A FilteringFileVisitor on a MacOS filesystem" should behave like visitor(setup = FileSystemSetup.MacOS)

  "A FilteringFileVisitor on a Windows filesystem" should behave like visitor(setup = FileSystemSetup.Windows)
}
