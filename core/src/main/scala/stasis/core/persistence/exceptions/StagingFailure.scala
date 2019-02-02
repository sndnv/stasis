package stasis.core.persistence.exceptions

final case class StagingFailure(override val message: String) extends PersistenceFailure(message)
