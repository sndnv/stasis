package stasis.core.persistence.exceptions

final case class ReservationFailure(override val message: String) extends PersistenceFailure(message)
