package stasis.core.networking.exceptions

final case class ReservationFailure(override val message: String) extends NetworkingFailure(message)
