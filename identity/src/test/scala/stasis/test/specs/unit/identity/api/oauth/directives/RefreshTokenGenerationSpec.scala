package stasis.test.specs.unit.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.RefreshTokenGeneration
import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.RefreshTokenStore
import stasis.identity.model.tokens.generators.{RandomRefreshTokenGenerator, RefreshTokenGenerator}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class RefreshTokenGenerationSpec extends RouteTest {
  "A RefreshTokenGeneration directive" should "generate refresh tokens" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val scope = Some("some-scope")

    val routes = directive.generateRefreshToken(client, owner, scope) {
      case Some(token) =>
        Directives.complete(StatusCodes.OK, token.value)

      case None =>
        Directives.complete(
          StatusCodes.InternalServerError,
          "Unexpected response received; no token generated"
        )
    }

    Get() ~> routes ~> check {
      val expectedToken = tokens.get(client).await match {
        case Some(storedToken) => storedToken.token.value
        case None              => fail("Unexpected response received; no token found")
      }

      status should be(StatusCodes.OK)
      responseAs[String] should be(expectedToken)
    }
  }

  it should "fail if refresh tokens could not be stored" in {
    val tokens = createFailingTokenStore(failingPut = true)
    val directive = createDirective(tokens)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val scope = Some("some-scope")

    val routes = directive.generateRefreshToken(client, owner, scope) {
      case Some(token) =>
        Directives.complete(StatusCodes.OK, token.value)

      case None =>
        Directives.complete(
          StatusCodes.InternalServerError,
          "Unexpected response received; no token generated"
        )
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  it should "fail if refresh token generation is not allowed" in {
    val tokens = createTokenStore()
    val directive = createDirective(tokens, withRefreshTokens = false)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val scope = Some("some-scope")

    val routes = directive.generateRefreshToken(client, owner, scope) {
      case Some(_) =>
        Directives.complete(
          StatusCodes.InternalServerError,
          "Unexpected response received; token generated"
        )

      case None =>
        Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      tokens.tokens.await should be(Map.empty)
    }
  }

  private def createDirective(
    tokens: RefreshTokenStore,
    withRefreshTokens: Boolean = true
  ) = new RefreshTokenGeneration {

    override protected def refreshTokensAllowed: Boolean = withRefreshTokens

    override implicit protected def mat: Materializer = ActorMaterializer()

    override protected def log: LoggingAdapter = createLogger()

    override protected def refreshTokenGenerator: RefreshTokenGenerator =
      new RandomRefreshTokenGenerator(tokenSize = 16)

    override protected def refreshTokenStore: RefreshTokenStore = tokens
  }
}
