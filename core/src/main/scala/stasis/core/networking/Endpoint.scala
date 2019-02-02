package stasis.core.networking

import stasis.core.security.NodeAuthenticator

trait Endpoint[C] {
  protected def authenticator: NodeAuthenticator[C]
}
