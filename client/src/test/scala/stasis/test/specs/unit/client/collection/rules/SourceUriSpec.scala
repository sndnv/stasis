package stasis.test.specs.unit.client.collection.rules

import stasis.client.collection.rules.SourceUri
import stasis.test.specs.unit.UnitSpec

class SourceUriSpec extends UnitSpec {
  "A SourceUri" should "extract the scheme from a source" in {
    val schemeTable: Map[String, Option[String]] = Map(
      // no scheme -> local filesystem
      "/home/test" -> None,
      "home/test" -> None,
      "/home/test path" -> None,
      "" -> None,

      // not schemes: single-char prefixes, numeric prefixes
      "C:\\test" -> None,
      "12:30" -> None,

      // library schemes
      "photos:/test/file.heic" -> Some("photos"),
      "photos://test/file.heic" -> Some("photos"),
      "photos:test" -> Some("photos"),
      "photos:" -> Some("photos"),
      "contacts:/" -> Some("contacts"),

      // scheme charset ([A-Za-z][A-Za-z0-9+.-]+) and case preservation
      "x-y.z+1:/a" -> Some("x-y.z+1"),
      "PHOTOS:/x" -> Some("PHOTOS")
    )

    schemeTable.foreach { case (source, expected) =>
      withClue(s"Extracting scheme from source [$source]") {
        SourceUri.scheme(source) should be(expected)
      }
    }
  }
}
