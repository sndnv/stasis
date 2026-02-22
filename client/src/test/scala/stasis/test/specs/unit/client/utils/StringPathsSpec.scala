package stasis.test.specs.unit.client.utils

import java.nio.file.FileSystem
import java.nio.file.FileSystems

import stasis.test.specs.unit.UnitSpec

class StringPathsSpec extends UnitSpec {
  import stasis.client.utils.StringPaths._

  "StringPaths" should "split paths into parent and name" in {
    "".splitParentAndName(fs) should be(("", ""))
    "/".splitParentAndName(fs) should be(("/", ""))
    "/a".splitParentAndName(fs) should be(("/", "a"))
    "/a/b".splitParentAndName(fs) should be(("/a", "b"))
    "/a/b/c".splitParentAndName(fs) should be(("/a/b", "c"))
    "/a/b/c.d".splitParentAndName(fs) should be(("/a/b", "c.d"))
    "/a/b/c.d/e".splitParentAndName(fs) should be(("/a/b/c.d", "e"))
    "C:\\a\\b".splitParentAndName(fs) should be(("", "C:\\a\\b"))
    "abc".splitParentAndName(fs) should be(("", "abc"))
    "./".splitParentAndName(fs) should be((".", ""))
    "./abc".splitParentAndName(fs) should be((".", "abc"))
    "./x/y/abc".splitParentAndName(fs) should be(("./x/y", "abc"))
  }

  they should "extract path name only" in {
    "".extractName(fs) should be("")
    "/".extractName(fs) should be("")
    "/a".extractName(fs) should be("a")
    "/a/b".extractName(fs) should be("b")
    "/a/b/c".extractName(fs) should be("c")
    "/a/b/c.d".extractName(fs) should be("c.d")
    "/a/b/c.d/e".extractName(fs) should be("e")
    "C:\\a\\b".extractName(fs) should be("C:\\a\\b")
    "abc".extractName(fs) should be("abc")
    "./".extractName(fs) should be("")
    "./abc".extractName(fs) should be("abc")
    "./x/y/abc".extractName(fs) should be("abc")
  }

  private val fs: FileSystem = FileSystems.getDefault
}
