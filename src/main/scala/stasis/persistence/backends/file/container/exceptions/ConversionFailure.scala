package stasis.persistence.backends.file.container.exceptions

case class ConversionFailure(override val message: String) extends ContainerFailure(message)
