package stasis.test.specs.unit.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthorizationCodeGeneration
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCodeStore
import stasis.identity.model.codes.generators.{AuthorizationCodeGenerator, DefaultAuthorizationCodeGenerator}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class AuthorizationCodeGenerationSpec extends RouteTest {
  "An AuthorizationCodeGeneration directive" should "generate authorization codes" in {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val redirectUri = Uri("http://example.com/")
    val state = "some-state"
    val owner = Generators.generateResourceOwner
    val scope = "some-scope"

    val routes = directive.generateAuthorizationCode(client, redirectUri, state, owner, scope = Some(scope)) {
      generatedCode =>
        Directives.complete(StatusCodes.OK, generatedCode.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      val expectedCode = codes.get(client).await
      Some(responseAs[String]) should be(expectedCode.map(_.code.value))
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

    val routes = directive.generateAuthorizationCode(client, redirectUri, state, owner, scope = Some(scope)) {
      generatedCode =>
        Directives.complete(StatusCodes.OK, generatedCode.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.Found)
      headers should contain(
        model.headers.Location(Uri(s"$redirectUri?error=server_error&state=$state"))
      )
    }
  }

  private def createDirective(
    codes: AuthorizationCodeStore
  ) = new AuthorizationCodeGeneration {
    override implicit protected def mat: Materializer = ActorMaterializer()

    override protected def log: LoggingAdapter = createLogger()

    override protected def authorizationCodeGenerator: AuthorizationCodeGenerator =
      new DefaultAuthorizationCodeGenerator(codeSize = 16)

    override protected def authorizationCodeStore: AuthorizationCodeStore = codes
  }
}
