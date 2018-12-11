package stasis.networking.exceptions

case class ReservationFailure(override val message: String) extends NetworkingFailure(message)
