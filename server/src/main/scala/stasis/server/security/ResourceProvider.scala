package stasis.server.security

import stasis.server.model.users.User

import scala.concurrent.Future
import scala.reflect.ClassTag

trait ResourceProvider {
  def provide[R <: Resource](user: User.Id)(implicit tag: ClassTag[R]): Future[R]
}
