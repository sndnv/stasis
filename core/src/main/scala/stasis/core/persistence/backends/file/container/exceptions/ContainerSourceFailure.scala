package stasis.core.persistence.backends.file.container.exceptions

final case class ContainerSourceFailure(override val message: String) extends ContainerFailure(message)
