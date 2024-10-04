package stasis.server.security

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.ClassTag

import stasis.server.model.users.UserStore
import stasis.server.security.exceptions.AuthorizationFailure

class DefaultResourceProvider(
  resources: Set[Resource],
  users: UserStore.View.Privileged
)(implicit ec: ExecutionContext)
    extends ResourceProvider {
  override def provide[R <: Resource](implicit user: CurrentUser, tag: ClassTag[R]): Future[R] =
    users.get(user.id).flatMap {
      case Some(userData) =>
        resources.collectFirst { case resource: R => resource } match {
          case Some(resource) =>
            if (userData.permissions.contains(resource.requiredPermission)) {
              Future.successful(resource)
            } else {
              Future.failed(
                AuthorizationFailure(
                  s"User [${user.id.toString}] does not have permission [${resource.requiredPermission.toString}] for resource [${resource.toString}]"
                )
              )
            }

          case None =>
            Future.failed(
              AuthorizationFailure(s"Resource [${tag.toString()}] requested by user [${user.id.toString}] was not found")
            )
        }

      case None =>
        Future.failed(
          AuthorizationFailure(s"User [${user.id.toString}] not found")
        )
    }
}

object DefaultResourceProvider {
  def apply(
    resources: Set[Resource],
    users: UserStore.View.Privileged
  )(implicit ec: ExecutionContext): DefaultResourceProvider =
    new DefaultResourceProvider(
      resources = resources,
      users = users
    )
}
