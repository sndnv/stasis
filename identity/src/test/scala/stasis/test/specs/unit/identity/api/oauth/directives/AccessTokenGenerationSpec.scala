package stasis.test.specs.unit.identity.api.oauth.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AccessTokenGeneration
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.AccessToken
import stasis.identity.model.tokens.generators.AccessTokenGenerator
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class AccessTokenGenerationSpec extends RouteTest {
  "An AccessTokenGeneration directive" should "generate access tokens for clients" in {
    val expectedToken = "some-token"

    val directive = new AccessTokenGeneration {
      override implicit protected def mat: Materializer = ActorMaterializer()
      override protected def accessTokenGenerator: AccessTokenGenerator = createGenerator(expectedToken)
    }

    val routes = directive.generateAccessToken(client = Generators.generateClient, audience = Seq.empty) { token =>
      Directives.complete(StatusCodes.OK, token.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(expectedToken)
    }
  }

  it should "generate access tokens for resource owners" in {
    val expectedToken = "some-token"

    val directive = new AccessTokenGeneration {
      override implicit protected def mat: Materializer = ActorMaterializer()
      override protected def accessTokenGenerator: AccessTokenGenerator = createGenerator(expectedToken)
    }

    val routes = directive.generateAccessToken(owner = Generators.generateResourceOwner, audience = Seq.empty) {
      token =>
        Directives.complete(StatusCodes.OK, token.value)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(expectedToken)
    }
  }

  private def createGenerator(token: String) = new AccessTokenGenerator {
    override def generate(client: Client, audience: Seq[Client]): AccessToken = AccessToken(value = token)
    override def generate(owner: ResourceOwner, audience: Seq[Api]): AccessToken = AccessToken(value = token)
  }
}
