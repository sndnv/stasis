package stasis.server.security.exceptions

import io.github.sndnv.layers.security.exceptions.SecurityFailure

final case class AuthorizationFailure(override val message: String) extends SecurityFailure(message)
