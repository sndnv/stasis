package stasis.test.specs.unit.core.packaging

import java.time.Instant
import java.util.UUID

import stasis.core.packaging.Manifest
import stasis.test.specs.unit.UnitSpec

class ManifestSpec extends UnitSpec {
  "A Manifest" should "be representable as string" in {
    val crate = "303da836-3abe-4376-bf9f-45c9acb643d7"
    val origin = "f266df3b-5c5d-46eb-8ad9-525da813af4f"
    val source = "9d567d2d-8055-486f-8c19-0cd4e0861ab5"

    val nodeA = "1547f7db-15f0-438e-b25c-26c220c67821"
    val nodeB = "cfbb0c3b-69b6-4abc-aba3-e2b827de5fae"
    val nodeC = "c77b2154-6afe-428e-9fb6-abdd2d957300"
    val now = Instant.now()

    val request = Manifest(
      crate = UUID.fromString(crate),
      size = 42,
      copies = 3,
      origin = UUID.fromString(origin),
      source = UUID.fromString(source),
      destinations = Seq(nodeA, nodeB, nodeC).map(UUID.fromString),
      created = now
    )

    request.toString should be(
      s"Manifest(crate=$crate,size=42,copies=3,origin=$origin,source=$source,destinations=[$nodeA,$nodeB,$nodeC],created=${now.toString})"
    )
  }
}
