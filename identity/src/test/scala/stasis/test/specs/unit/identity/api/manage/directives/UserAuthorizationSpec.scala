package stasis.test.specs.unit.identity.api.manage.directives

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import org.slf4j.Logger

import stasis.identity.api.manage.directives.UserAuthorization
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class UserAuthorizationSpec extends RouteTest {
  "A UserAuthorization directive" should "authorize users with appropriate scopes" in withRetry {
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

  it should "fail to authorize users without appropriate scopes" in withRetry {
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
      override protected def log: Logger = createLogger()
    }
}
