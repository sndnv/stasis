package stasis.server.api.routes

import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.server.security.{CurrentUser, Resource, ResourceProvider}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait ApiRoutes extends EntityDiscardingDirectives {
  def resource[R1 <: Resource](f: R1 => Future[Route])(
    implicit
    provider: ResourceProvider,
    user: CurrentUser,
    ec: ExecutionContext,
    t: ClassTag[R1]
  ): Route =
    onSuccess(
      for {
        resource1 <- provider.provide[R1]
        result <- f(resource1)
      } yield result
    )(identity)

  def resources[R1 <: Resource, R2 <: Resource](f: (R1, R2) => Future[Route])(
    implicit
    provider: ResourceProvider,
    user: CurrentUser,
    ec: ExecutionContext,
    t1: ClassTag[R1],
    t2: ClassTag[R2]
  ): Route =
    onSuccess(
      for {
        resource1 <- provider.provide[R1]
        resource2 <- provider.provide[R2]
        result <- f(resource1, resource2)
      } yield result
    )(identity)

  def resources[R1 <: Resource, R2 <: Resource, R3 <: Resource](f: (R1, R2, R3) => Future[Route])(
    implicit
    provider: ResourceProvider,
    user: CurrentUser,
    ec: ExecutionContext,
    t1: ClassTag[R1],
    t2: ClassTag[R2],
    t3: ClassTag[R3]
  ): Route =
    onSuccess(
      for {
        resource1 <- provider.provide[R1]
        resource2 <- provider.provide[R2]
        resource3 <- provider.provide[R3]
        result <- f(resource1, resource2, resource3)
      } yield result
    )(identity)

  implicit def routeContextToExecutionContext(implicit ctx: RoutesContext): ExecutionContext = ctx.ec

  implicit def routeContextToResourceProvider(implicit ctx: RoutesContext): ResourceProvider = ctx.resourceProvider

  def log(implicit ctx: RoutesContext): Logger = ctx.log
}
