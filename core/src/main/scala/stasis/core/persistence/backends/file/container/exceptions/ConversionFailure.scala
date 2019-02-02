package stasis.core.persistence.backends.file.container.exceptions

final case class ConversionFailure(override val message: String) extends ContainerFailure(message)
