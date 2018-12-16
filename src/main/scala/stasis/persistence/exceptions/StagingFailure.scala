package stasis.persistence.exceptions

case class StagingFailure(override val message: String) extends PersistenceFailure(message)
