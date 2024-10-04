package stasis.server.security.exceptions

import stasis.layers.security.exceptions.SecurityFailure

final case class AuthorizationFailure(override val message: String) extends SecurityFailure(message)
