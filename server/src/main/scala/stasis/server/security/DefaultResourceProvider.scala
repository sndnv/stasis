package stasis.server.security

import stasis.server.model.users.UserStore
import stasis.server.security.exceptions.AuthorizationFailure

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

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
                  s"User [${user.id}] does not have permission [${resource.requiredPermission}] for resource [$resource]"
                )
              )
            }

          case None =>
            Future.failed(
              AuthorizationFailure(s"Resource [$tag] requested by user [${user.id}] was not found")
            )
        }

      case None =>
        Future.failed(
          AuthorizationFailure(s"User [${user.id}] not found")
        )
    }
}
