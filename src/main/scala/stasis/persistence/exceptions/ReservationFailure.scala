package stasis.persistence.exceptions

case class ReservationFailure(override val message: String) extends PersistenceFailure(message)
