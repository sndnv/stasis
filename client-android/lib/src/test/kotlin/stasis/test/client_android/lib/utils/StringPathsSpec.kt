package stasis.test.client_android.lib.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.utils.StringPaths.extractName
import stasis.client_android.lib.utils.StringPaths.splitParentAndName
import java.nio.file.FileSystems

class StringPathsSpec : WordSpec({
    "StringPaths" should {
        val fs = FileSystems.getDefault()

        "split paths into parent and name" {
            "".splitParentAndName(fs) shouldBe (Pair("", ""))
            "/".splitParentAndName(fs) shouldBe (Pair("/", ""))
            "/a".splitParentAndName(fs) shouldBe (Pair("/", "a"))
            "/a/b".splitParentAndName(fs) shouldBe (Pair("/a", "b"))
            "/a/b/c".splitParentAndName(fs) shouldBe (Pair("/a/b", "c"))
            "/a/b/c.d".splitParentAndName(fs) shouldBe (Pair("/a/b", "c.d"))
            "/a/b/c.d/e".splitParentAndName(fs) shouldBe (Pair("/a/b/c.d", "e"))
            "C:\\a\\b".splitParentAndName(fs) shouldBe (Pair("", "C:\\a\\b"))
            "abc".splitParentAndName(fs) shouldBe (Pair("", "abc"))
            "./".splitParentAndName(fs) shouldBe (Pair(".", ""))
            "./abc".splitParentAndName(fs) shouldBe (Pair(".", "abc"))
            "./x/y/abc".splitParentAndName(fs) shouldBe (Pair("./x/y", "abc"))
        }

        "extract path name only" {
            "".extractName(fs) shouldBe ("")
            "/".extractName(fs) shouldBe ("")
            "/a".extractName(fs) shouldBe ("a")
            "/a/b".extractName(fs) shouldBe ("b")
            "/a/b/c".extractName(fs) shouldBe ("c")
            "/a/b/c.d".extractName(fs) shouldBe ("c.d")
            "/a/b/c.d/e".extractName(fs) shouldBe ("e")
            "C:\\a\\b".extractName(fs) shouldBe ("C:\\a\\b")
            "abc".extractName(fs) shouldBe ("abc")
            "./".extractName(fs) shouldBe ("")
            "./abc".extractName(fs) shouldBe ("abc")
            "./x/y/abc".extractName(fs) shouldBe ("abc")
        }
    }
})
