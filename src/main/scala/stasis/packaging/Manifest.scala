package stasis.packaging

import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class Manifest(
  crate: Crate.Id,
  copies: Int,
  retention: FiniteDuration,
  source: Node,
  destinations: Seq[Node] = Seq.empty
)

object Manifest {
  final case class Config(
    defaultCopies: Int,
    defaultRetention: FiniteDuration,
    getManifestErrors: Manifest => Seq[Manifest.FieldError]
  )

  final case class FieldError(field: String, error: String)
}
