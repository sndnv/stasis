package stasis.security

import akka.http.scaladsl.model.headers.HttpCredentials
import stasis.routing.Node

trait NodeAuthenticator {
  def authenticate(credentials: HttpCredentials): Option[Node]
  def provide(): HttpCredentials
}
