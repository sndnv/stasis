package stasis.server.security

import scala.concurrent.Future
import scala.reflect.ClassTag

trait ResourceProvider {
  def provide[R <: Resource](implicit user: CurrentUser, tag: ClassTag[R]): Future[R]
}
