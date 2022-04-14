package stasis.test.specs.unit.identity.api.oauth.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import org.slf4j.Logger
import play.api.libs.json._
import stasis.identity.api.oauth.directives.RefreshTokenConsumption
import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.RefreshTokenStore
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.ExecutionContext

class RefreshTokenConsumptionSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "A RefreshTokenConsumption directive" should "validate provided and stored scopes" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens)

    directive.providedScopeAllowed(
      stored = Some("a b c d"),
      provided = Some("b c")
    ) should be(true) // provided in stored

    directive.providedScopeAllowed(
      stored = Some("a b c d"),
      provided = Some("d e")
    ) should be(false) // provided NOT in stored

    directive.providedScopeAllowed(
      stored = Some("a b c d"),
      provided = None
    ) should be(false) // provided is empty

    directive.providedScopeAllowed(
      stored = None,
      provided = Some("b c")
    ) should be(false) // stored is empty
  }

  it should "consume valid refresh tokens with expected scopes" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken
    val scope = Some("some-scope")

    val routes = directive.consumeRefreshToken(client, scope, token) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    tokens.put(client, token, owner, scope).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(owner.username)
      tokens.tokens.await should be(Map.empty)
    }
  }

  it should "fail to consume valid refresh tokens without needed scopes" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken
    val scope = Some("some-scope")

    val routes = directive.consumeRefreshToken(client, providedScope = Some("other-scope"), token) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    tokens.put(client, token, owner, scope).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_scope"))
    }
  }

  it should "fail if the provided and found refresh tokens do not match" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken
    val scope = Some("some-scope")

    val routes = directive.consumeRefreshToken(client, scope, Generators.generateRefreshToken) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    tokens.put(client, token, owner, scope).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "fail if the provided and found refresh token clients do not match" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken
    val scope = Some("some-scope")

    val routes = directive.consumeRefreshToken(Client.generateId(), scope, token) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    tokens.put(client, token, owner, scope).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "fail if refresh tokens could not be queried" in {
    val tokens = createFailingTokenStore(failingGet = true)
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken
    val scope = Some("some-scope")

    val routes = directive.consumeRefreshToken(client, scope, token) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    tokens.put(client, token, owner, scope).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  private def createDirective(tokens: RefreshTokenStore) =
    new RefreshTokenConsumption {
      override implicit protected def ec: ExecutionContext = system.dispatcher
      override protected def log: Logger = createLogger()
      override protected def refreshTokenStore: RefreshTokenStore = tokens
    }
}
