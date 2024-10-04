package stasis.server.security.users

import scala.concurrent.Future

import stasis.shared.model.users.User

trait UserCredentialsManager {
  def id: String
  def createResourceOwner(user: User, username: String, rawPassword: String): Future[UserCredentialsManager.Result]
  def activateResourceOwner(user: User.Id): Future[UserCredentialsManager.Result]
  def deactivateResourceOwner(user: User.Id): Future[UserCredentialsManager.Result]
  def setResourceOwnerPassword(user: User.Id, rawPassword: String): Future[UserCredentialsManager.Result]
}

object UserCredentialsManager {
  sealed trait Result
  object Result {
    case object Success extends Result
    final case class NotFound(message: String) extends Result
    final case class Conflict(message: String) extends Result
  }
}
