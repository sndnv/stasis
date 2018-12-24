package stasis.networking

import stasis.security.NodeAuthenticator

trait Endpoint[C] {
  protected def authenticator: NodeAuthenticator[C]
}
