package stasis.layers.security.exceptions

final case class ProviderFailure(override val message: String) extends SecurityFailure(message)
