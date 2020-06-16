package stasis.test.specs.unit.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives
import akka.stream.{Materializer, SystemMaterializer}
import stasis.identity.api.oauth.directives.AuthorizationCodeGeneration
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.generators.{AuthorizationCodeGenerator, DefaultAuthorizationCodeGenerator}
import stasis.identity.model.codes.{AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.errors.AuthorizationError
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class AuthorizationCodeGenerationSpec extends RouteTest {
  "An AuthorizationCodeGeneration directive" should "generate authorization codes without associated challenges" in {
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

      codes.codes.await.headOption match {
        case Some((_, expectedCode)) =>
          responseAs[String] should be(expectedCode.code.value)
          expectedCode.challenge should be(None)

        case None =>
          fail("Expected code but none was found")
      }
    }
  }

  it should "generate authorization codes with associated challenges" in {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val redirectUri = Uri("http://example.com/")
    val state = "some-state"
    val owner = Generators.generateResourceOwner
    val scope = "some-scope"
    val expectedChallenge = StoredAuthorizationCode.Challenge(
      stasis.test.Generators.generateString(withSize = 128),
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

      codes.codes.await.headOption match {
        case Some((_, expectedCode)) =>
          responseAs[String] should be(expectedCode.code.value)
          expectedCode.challenge should be(Some(expectedChallenge))

        case None =>
          fail("Expected code but none was found")
      }
    }
  }

  it should "fail if authorization codes could not be stored" in {
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
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer

      override protected def log: LoggingAdapter = createLogger()

      override protected def authorizationCodeGenerator: AuthorizationCodeGenerator =
        new DefaultAuthorizationCodeGenerator(codeSize = 16)

      override protected def authorizationCodeStore: AuthorizationCodeStore = codes
    }
}
