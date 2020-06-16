package stasis.test.specs.unit.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{Materializer, SystemMaterializer}
import stasis.identity.api.manage.directives.UserAuthorization
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class UserAuthorizationSpec extends RouteTest {
  "A UserAuthorization directive" should "authorize users with appropriate scopes" in {
    val directive = createDirective()
    val owner = Generators.generateResourceOwner

    val routes = directive.authorize(
      user = owner,
      scope = owner.allowedScopes.headOption.getOrElse("missing-scope")
    ) {
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "fail to authorize users without appropriate scopes" in {
    val directive = createDirective()
    val owner = Generators.generateResourceOwner

    val routes = directive.authorize(
      user = owner,
      scope = "missing-scope"
    ) {
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.Forbidden)
    }
  }

  private def createDirective() =
    new UserAuthorization {
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override protected def log: LoggingAdapter = createLogger()
    }
}
