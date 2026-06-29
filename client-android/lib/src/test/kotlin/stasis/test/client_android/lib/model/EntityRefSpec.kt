package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.EntityRef
import java.nio.file.Paths

class EntityRefSpec : WordSpec({
    "An EntityRef.Filesystem" should {
        "provide its key as an absolute path" {
            EntityRef.Filesystem(Paths.get("/tmp/a/b")).key shouldBe ("/tmp/a/b")
        }

        "map its path while remaining a filesystem ref" {
            val ref = EntityRef.Filesystem(Paths.get("/tmp/a/b"))

            ref.mapFilesystem { it.parent } shouldBe (EntityRef.Filesystem(Paths.get("/tmp/a")))
            ref.mapLibrary { scheme, path -> scheme to path } shouldBe (ref)
        }

        "be coercible to a filesystem ref" {
            val ref = EntityRef.Filesystem(Paths.get("/tmp/a/b"))

            ref.asFilesystem() shouldBe (ref)
        }

        "fail to be coerced to a library ref" {
            val ref = EntityRef.Filesystem(Paths.get("/tmp/a/b"))

            val e = shouldThrow<IllegalArgumentException> { ref.asLibrary() }
            e.message shouldBe ("Requested a library reference but [/tmp/a/b] found")
        }
    }

    "An EntityRef.Library" should {
        "provide its key as [scheme:path]" {
            EntityRef.Library(scheme = "photos", path = "/album/img.heic").key shouldBe ("photos:/album/img.heic")
        }

        "map its scheme and path while remaining a library ref" {
            val ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic")

            ref.mapLibrary { scheme, path -> scheme to "$path.bak" } shouldBe (
                EntityRef.Library(scheme = "photos", path = "/album/img.heic.bak")
            )
            ref.mapFilesystem { it.parent } shouldBe (ref)
        }

        "be coercible to a library ref" {
            val ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic")

            ref.asLibrary() shouldBe (ref)
        }

        "fail to be coerced to a filesystem ref" {
            val ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic")

            val e = shouldThrow<IllegalArgumentException> { ref.asFilesystem() }
            e.message shouldBe ("Requested a filesystem reference but [photos:/album/img.heic] found")
        }
    }

    "An EntityRef" should {
        "support flat-mapping across kinds" {
            val filesystem = EntityRef.Filesystem(Paths.get("/tmp/a"))

            filesystem.flatMap { EntityRef.Library(scheme = "photos", path = "/album") } shouldBe (
                EntityRef.Library(scheme = "photos", path = "/album")
            )
        }

        "be reconstructable from a filesystem key" {
            EntityRef.default("/tmp/a/b") shouldBe (EntityRef.Filesystem(Paths.get("/tmp/a/b")))
        }

        "be reconstructable from a library key" {
            EntityRef.default("photos:/album/img.heic") shouldBe (
                EntityRef.Library(scheme = "photos", path = "/album/img.heic")
            )
        }

        "round-trip a library key through [default] and [key]" {
            val key = "photos:/album/img.heic"
            EntityRef.default(key).key shouldBe (key)
        }
    }
})
