package stasis.identity.api.oauth.directives

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.server.Directives
import org.slf4j.Logger

import stasis.identity.RouteTest
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.Generators
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.codes.generators.DefaultAuthorizationCodeGenerator
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.layers

class AuthorizationCodeGenerationSpec extends RouteTest {
  "An AuthorizationCodeGeneration directive" should "generate authorization codes without associated challenges" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val redirectUri = Uri("http://example.com/")
    val state = "some-state"
    val owner = Generators.generateResourceOwner
    val scope = "some-scope"

    val routes = directive.generateAuthorizationCode(
      client,
      redirectUri,
      state,
      owner,
      scope = Some(scope)
    ) { generatedCode =>
      Directives.complete(StatusCodes.OK, generatedCode.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)

      codes.all.await.headOption match {
        case Some(expectedCode) =>
          responseAs[String] should be(expectedCode.code.value)
          expectedCode.challenge should be(None)

        case None =>
          fail("Expected code but none was found")
      }
    }
  }

  it should "generate authorization codes with associated challenges" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val redirectUri = Uri("http://example.com/")
    val state = "some-state"
    val owner = Generators.generateResourceOwner
    val scope = "some-scope"
    val expectedChallenge = StoredAuthorizationCode.Challenge(
      layers.Generators.generateString(withSize = 128),
      Some(ChallengeMethod.S256)
    )

    val routes = directive.generateAuthorizationCode(
      client,
      redirectUri,
      state,
      owner,
      scope = Some(scope),
      challenge = expectedChallenge.value,
      challengeMethod = expectedChallenge.method
    ) { generatedCode =>
      Directives.complete(StatusCodes.OK, generatedCode.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)

      codes.all.await.headOption match {
        case Some(expectedCode) =>
          responseAs[String] should be(expectedCode.code.value)
          expectedCode.challenge should be(Some(expectedChallenge))

        case None =>
          fail("Expected code but none was found")
      }
    }
  }

  it should "fail if authorization codes could not be stored" in withRetry {
    val codes = createFailingCodeStore(failingPut = true)
    val directive = createDirective(codes)

    val client = Client.generateId()
    val redirectUri = Uri("http://example.com/")
    val state = "some-state"
    val owner = Generators.generateResourceOwner
    val scope = "some-scope"

    val routes = directive.generateAuthorizationCode(client, redirectUri, state, owner, scope = Some(scope)) { generatedCode =>
      Directives.complete(StatusCodes.OK, generatedCode.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.Found)
      headers should contain(
        model.headers.Location(redirectUri.withQuery(AuthorizationError.ServerError(withState = state).asQuery))
      )
    }
  }

  private def createDirective(
    codes: AuthorizationCodeStore
  ) =
    new AuthorizationCodeGeneration {
      override protected def log: Logger = createLogger()

      override protected def authorizationCodeGenerator: AuthorizationCodeGenerator =
        new DefaultAuthorizationCodeGenerator(codeSize = 16)

      override protected def authorizationCodeStore: AuthorizationCodeStore = codes
    }
}
