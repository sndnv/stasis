package stasis.test.client_android.lib.ops.recovery

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.ops.recovery.RecoverySourceKind

class RecoverySourceKindSpec : WordSpec({
    "A RecoverySourceKind" should {
        "map entities to their source kind" {
            val table: Map<String, RecoverySourceKind> = mapOf(
                "/home/test" to RecoverySourceKind.Filesystem,
                "home/test" to RecoverySourceKind.Filesystem,
                "" to RecoverySourceKind.Filesystem,
                "C:\\test" to RecoverySourceKind.Filesystem,
                "calendar:/uid" to RecoverySourceKind.Library("calendar"),
                "contacts:/id" to RecoverySourceKind.Library("contacts"),
                "photos://album/file.heic" to RecoverySourceKind.Library("photos")
            )

            table.forEach { (entity, expected) ->
                withClue("Mapping entity [$entity]") {
                    RecoverySourceKind.forEntity(entity) shouldBe (expected)
                }
            }
        }

        "treat libraries with different schemes as distinct" {
            RecoverySourceKind.Library("calendar") shouldBe (RecoverySourceKind.Library("calendar"))
            (RecoverySourceKind.Library("calendar") == RecoverySourceKind.Library("contacts")) shouldBe (false)
            (RecoverySourceKind.Library("calendar") == RecoverySourceKind.Filesystem) shouldBe (false)
        }
    }
})
