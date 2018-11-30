package stasis.packaging

import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

case class Manifest(
  crate: Crate.Id,
  copies: Int,
  retention: FiniteDuration,
  source: Node
)

object Manifest {
  case class Config(
    defaultCopies: Int,
    defaultRetention: FiniteDuration,
    getManifestErrors: Manifest => Seq[Manifest.FieldError]
  )

  case class FieldError(field: String, error: String)
}
