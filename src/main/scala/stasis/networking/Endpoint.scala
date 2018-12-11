package stasis.networking

import stasis.security.NodeAuthenticator

trait Endpoint[C] {
  protected val authenticator: NodeAuthenticator[C]
}
