package stasis.layers.service.bootstrap

final case class BootstrapResult(found: Int, created: Int) {
  def +(other: BootstrapResult): BootstrapResult =
    BootstrapResult(found = found + other.found, created = created + other.created)
}

object BootstrapResult {
  def empty: BootstrapResult = BootstrapResult(found = 0, created = 0)
}
