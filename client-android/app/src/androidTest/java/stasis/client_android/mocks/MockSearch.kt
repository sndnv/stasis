package stasis.client_android.mocks

import stasis.client_android.lib.ops.search.Search
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import java.time.Instant

class MockSearch : Search {
    override suspend fun search(query: Regex, until: Instant?): Try<Search.Result> =
        Success(Search.Result(definitions = emptyMap()))
}
