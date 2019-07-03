package stasis.test.specs.unit.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import stasis.identity.api.manage.directives.UserAuthorization
import stasis.identity.model.realms.Realm
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class UserAuthorizationSpec extends RouteTest {
  "A UserAuthorization directive" should "authorize users in the master realm" in {
    val directive = createDirective()
    val owner = Generators.generateResourceOwner.copy(realm = Realm.Master)

    val routes = directive.authorize(
      user = owner,
      targetRealm = Realm.Master,
      scope = "some-scope"
    ) {
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "authorize users in the target realm with appropriate scopes" in {
    val directive = createDirective()
    val realm = Generators.generateRealm
    val owner = Generators.generateResourceOwner.copy(realm = realm.id)

    val routes = directive.authorize(
      user = owner,
      targetRealm = realm.id,
      scope = owner.allowedScopes.headOption.getOrElse("missing-scope")
    ) {
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "fail to authorize users in the target realm without appropriate scopes" in {
    val directive = createDirective()
    val realm = Generators.generateRealm
    val owner = Generators.generateResourceOwner.copy(realm = realm.id)

    val routes = directive.authorize(
      user = owner,
      targetRealm = realm.id,
      scope = "missing-scope"
    ) {
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.Forbidden)
    }
  }

  it should "fail to authorize users not in the target realm" in {
    val directive = createDirective()
    val realm = Generators.generateRealm
    val owner = Generators.generateResourceOwner

    val routes = directive.authorize(
      user = owner,
      targetRealm = realm.id,
      scope = owner.allowedScopes.headOption.getOrElse("missing-scope")
    ) {
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.Forbidden)
    }
  }

  private def createDirective() = new UserAuthorization {
    override implicit protected def mat: Materializer = ActorMaterializer()
    override protected def log: LoggingAdapter = createLogger()
  }
}
