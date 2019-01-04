package stasis.persistence.backends.file.container.exceptions

case class ContainerSinkFailure(override val message: String) extends ContainerFailure(message)
