package stasis.core.security

import scala.concurrent.Future

import stasis.core.routing.Node

trait NodeAuthenticator[C] {
  def authenticate(credentials: C): Future[Node.Id]
}
