package stasis.test.specs.unit.shared.interop.core

import java.time.Instant
import java.util.UUID

import stasis.core.api.Formats._
import stasis.core.packaging.Manifest
import stasis.shared.api.Formats._
import stasis.shared.api.responses.DeletedManifest
import stasis.test.specs.unit.shared.interop.InteropSpec

class ManifestInteropSpec extends InteropSpec {
  "Manifest interop" should "decode and re-encode a manifest" in {
    assert(
      domain = "core",
      resource = "Manifest",
      matches = Manifest(
        crate = UUID.fromString("daa9dd9e-828d-4810-b34f-736d3b742aad"),
        size = 8388608L,
        copies = 3,
        origin = UUID.fromString("c4db10f8-a72c-456f-9f6b-b9ef650e1f3f"),
        source = UUID.fromString("71cb1be7-140d-4a85-b9b1-a3559e11f73f"),
        destinations = Seq(
          UUID.fromString("8c4efe24-1c2d-44de-a437-40f22e1a9aac"),
          UUID.fromString("341bb614-d228-4cbc-a4cf-ac5afaa1e50b")
        ),
        created = Instant.parse("2026-02-10T14:20:00Z")
      )
    )
  }

  it should "decode and re-encode DeletedManifest" in {
    assert(
      domain = "core",
      resource = "DeletedManifest",
      matches = DeletedManifest(existing = true)
    )
  }
}
