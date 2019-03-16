package stasis.test.specs.unit.server.security.mocks

import stasis.server.security.exceptions.AuthorizationFailure
import stasis.server.security.{CurrentUser, Resource, ResourceProvider}

import scala.concurrent.Future
import scala.reflect.ClassTag

class MockResourceProvider(resources: Set[Resource]) extends ResourceProvider {
  override def provide[R <: Resource](implicit user: CurrentUser, tag: ClassTag[R]): Future[R] =
    resources.collectFirst { case resource: R => resource } match {
      case Some(resource) =>
        Future.successful(resource)

      case None =>
        Future.failed(
          AuthorizationFailure(s"Resource [$tag] requested by user [${user.id}] was not found")
        )
    }
}
