package stasis.server.security

import stasis.core.persistence.backends.KeyValueBackend
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.server.model.users.User

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class DefaultResourceProvider(
  resources: Set[Resource],
  users: KeyValueBackend[User.Id, User]
)(implicit ec: ExecutionContext)
    extends ResourceProvider {
  override def provide[R <: Resource](user: User.Id)(implicit tag: ClassTag[R]): Future[R] =
    users.get(user).flatMap {
      case Some(userData) =>
        resources.collectFirst { case resource: R => resource } match {
          case Some(resource) =>
            if (userData.permissions.contains(resource.requiredPermission)) {
              Future.successful(resource)
            } else {
              Future.failed(
                AuthorizationFailure(
                  s"User [$user] does not have permission [${resource.requiredPermission}] for resource [$resource]"
                )
              )
            }

          case None =>
            Future.failed(
              AuthorizationFailure(s"Resource [$tag] requested by user [$user] was not found")
            )
        }

      case None =>
        Future.failed(
          AuthorizationFailure(s"User [$user] not found")
        )
    }
}
