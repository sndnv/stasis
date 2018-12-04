package stasis.networking

import stasis.packaging.{Crate, Manifest}

trait Endpoint

object Endpoint {
  import play.api.libs.json.{Format, Json}

  case class CrateCreated(
    crateId: Crate.Id,
    copies: Int,
    retention: Long
  )

  object CrateCreated {
    def apply(manifest: Manifest): CrateCreated =
      CrateCreated(manifest.crate, manifest.copies, manifest.retention.toSeconds)
  }

  implicit val crateCreatedFormat: Format[CrateCreated] = Json.format[CrateCreated]

  implicit val manifestErrorSeqFormat: Format[Manifest.FieldError] = Json.format[Manifest.FieldError]
}
