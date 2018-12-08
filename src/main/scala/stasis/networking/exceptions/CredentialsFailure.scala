package stasis.networking.exceptions

case class CredentialsFailure(override val message: String) extends NetworkingFailure(message)
