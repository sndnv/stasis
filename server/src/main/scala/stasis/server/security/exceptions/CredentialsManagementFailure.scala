package stasis.server.security.exceptions

final case class CredentialsManagementFailure(message: String) extends Exception(message)
