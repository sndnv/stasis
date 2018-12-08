package stasis.security

import stasis.routing.Node

trait NodeAuthenticator[C] {
  def authenticate(credentials: C): Option[Node]
}
