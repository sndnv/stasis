package stasis.networking.exceptions

final case class CredentialsFailure(override val message: String) extends NetworkingFailure(message)
