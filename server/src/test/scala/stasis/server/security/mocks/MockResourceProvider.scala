package stasis.server.security.mocks

import scala.concurrent.Future
import scala.reflect.ClassTag

import stasis.server.security.CurrentUser
import stasis.server.security.Resource
import stasis.server.security.ResourceProvider
import stasis.server.security.exceptions.AuthorizationFailure

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

object MockResourceProvider {
  def apply(): MockResourceProvider = new MockResourceProvider(resources = Set.empty)
}
