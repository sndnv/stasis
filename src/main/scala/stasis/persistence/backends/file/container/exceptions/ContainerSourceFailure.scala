package stasis.persistence.backends.file.container.exceptions

case class ContainerSourceFailure(override val message: String) extends ContainerFailure(message)
