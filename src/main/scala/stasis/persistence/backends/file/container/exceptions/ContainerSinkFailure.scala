package stasis.persistence.backends.file.container.exceptions

final case class ContainerSinkFailure(override val message: String) extends ContainerFailure(message)
