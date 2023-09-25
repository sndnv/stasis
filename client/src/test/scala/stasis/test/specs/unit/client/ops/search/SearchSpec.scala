package stasis.test.specs.unit.client.ops.search

import stasis.client.ops.search.Search
import stasis.test.specs.unit.AsyncUnitSpec

class SearchSpec extends AsyncUnitSpec {
  "A Search Query" should "converting query strings to regex patterns" in {
    Search.Query(query = "abc").pattern.pattern() should be(".*abc.*")
    Search.Query(query = "a-b_c").pattern.pattern() should be(".*a-b_c.*")
    Search.Query(query = "[abc").pattern.pattern() should be("[abc")
    Search.Query(query = ".*abc").pattern.pattern() should be(".*abc")
  }
}
