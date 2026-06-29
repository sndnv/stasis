package stasis.test.client_android.lib.collection.rules

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.collection.rules.SourceUri

class SourceUriSpec : WordSpec({
    "A SourceUri" should {
        "extract the scheme from a source" {
            val schemeTable: Map<String, String?> = mapOf(
                "/home/test" to null,
                "home/test" to null,
                "/home/test path" to null,
                "" to null,

                "C:\\test" to null,
                "12:30" to null,

                "photos:/test/file.heic" to "photos",
                "photos://test/file.heic" to "photos",
                "photos:test" to "photos",
                "photos:" to "photos",
                "contacts:/" to "contacts",

                "x-y.z+1:/a" to "x-y.z+1",
                "PHOTOS:/x" to "PHOTOS"
            )

            schemeTable.forEach { (source, expected) ->
                withClue("Extracting scheme from source [$source]") {
                    SourceUri.scheme(source) shouldBe (expected)
                }
            }
        }
    }
})
