package stasis.core.security

import stasis.core.routing.Node

import scala.concurrent.Future

trait NodeAuthenticator[C] {
  def authenticate(credentials: C): Future[Node.Id]
}
