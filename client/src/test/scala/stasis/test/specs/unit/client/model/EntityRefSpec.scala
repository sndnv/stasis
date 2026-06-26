package stasis.test.specs.unit.client.model

import java.nio.file.Paths

import stasis.client.model.EntityRef
import stasis.test.specs.unit.UnitSpec

class EntityRefSpec extends UnitSpec {
  "An EntityRef.Filesystem" should "provide its key as an absolute path" in {
    EntityRef.Filesystem(Paths.get("/tmp/a/b")).key should be("/tmp/a/b")
  }

  it should "map its path while remaining a filesystem ref" in {
    val ref = EntityRef.Filesystem(Paths.get("/tmp/a/b"))

    ref.mapFilesystem(_.getParent) should be(EntityRef.Filesystem(Paths.get("/tmp/a")))
    ref.mapLibrary((scheme, path) => (scheme, path)) should be(ref)
  }

  it should "be coercible to a filesystem ref" in {
    val ref = EntityRef.Filesystem(Paths.get("/tmp/a/b"))

    ref.asFilesystem should be(ref)
  }

  it should "fail to be coerced to a library ref" in {
    val ref = EntityRef.Filesystem(Paths.get("/tmp/a/b"))

    val e = intercept[IllegalArgumentException](ref.asLibrary)
    e.getMessage should be("Requested a library reference but [/tmp/a/b] found")
  }

  "An EntityRef.Library" should "provide its key as [scheme:path]" in {
    EntityRef.Library(scheme = "photos", path = "/album/img.heic").key should be("photos:/album/img.heic")
  }

  it should "map its scheme and path while remaining a library ref" in {
    val ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic")

    ref.mapLibrary((scheme, path) => (scheme, s"$path.bak")) should be(
      EntityRef.Library(scheme = "photos", path = "/album/img.heic.bak")
    )
    ref.mapFilesystem(_.getParent) should be(ref)
  }

  it should "be creatable from a (scheme, path) pair" in {
    EntityRef.Library(("photos", "/album")) should be(EntityRef.Library(scheme = "photos", path = "/album"))
  }

  it should "be coercible to a library ref" in {
    val ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic")

    ref.asLibrary should be(ref)
  }

  it should "fail to be coerced to a filesystem ref" in {
    val ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic")

    val e = intercept[IllegalArgumentException](ref.asFilesystem)
    e.getMessage should be("Requested a filesystem reference but [photos:/album/img.heic] found")
  }

  "An EntityRef" should "support flat-mapping across kinds" in {
    val filesystem = EntityRef.Filesystem(Paths.get("/tmp/a"))

    filesystem.flatMap(_ => EntityRef.Library(scheme = "photos", path = "/album")) should be(
      EntityRef.Library(scheme = "photos", path = "/album")
    )
  }

  it should "be reconstructable from a filesystem key" in {
    EntityRef.default("/tmp/a/b") should be(EntityRef.Filesystem(Paths.get("/tmp/a/b")))
  }

  it should "be reconstructable from a library key" in {
    EntityRef.default("photos:/album/img.heic") should be(
      EntityRef.Library(scheme = "photos", path = "/album/img.heic")
    )
  }

  it should "round-trip a library key through [default] and [key]" in {
    val key = "photos:/album/img.heic"
    EntityRef.default(key).key should be(key)
  }
}
