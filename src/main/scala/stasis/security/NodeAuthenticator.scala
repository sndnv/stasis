package stasis.security

import stasis.routing.Node

import scala.concurrent.Future

trait NodeAuthenticator[C] {
  def authenticate(credentials: C): Future[Node.Id]
}
