package stasis.test.specs.unit.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import play.api.libs.json._
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.AuthorizationCodeConsumption
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCodeStore
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.ExecutionContext

class AuthorizationCodeConsumptionSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "An AuthorizationCodeConsumption directive" should "consume valid authorization codes" in {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) {
      case (extractedOwner, extractedScope) =>
        Directives.complete(
          StatusCodes.OK,
          Json.obj(
            "owner" -> JsString(extractedOwner.username),
            "scope" -> Json.toJson(extractedScope)
          )
        )
    }

    codes.put(client, code, owner, scope = Some(scope)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      val fields = responseAs[JsObject].fields
      fields should contain("owner" -> JsString(owner.username))
      fields should contain("scope" -> JsString(scope))
      codes.codes.await should be(Map.empty)
    }
  }

  it should "fail if the provided and found authorization codes do not match" in {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) {
      case (_, _) =>
        Directives.complete(StatusCodes.OK)
    }

    codes.put(client, Generators.generateAuthorizationCode, owner, scope = Some(scope)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_grant"))
    }
  }

  it should "fail if no authorization codes are found for clients" in {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val code = Generators.generateAuthorizationCode

    val routes = directive.consumeAuthorizationCode(client, code) {
      case (_, _) =>
        Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_grant"))
    }
  }

  it should "fail if authorization codes could not be queried" in {
    val codes = createFailingCodeStore(failingGet = true)
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) {
      case (extractedOwner, extractedScope) =>
        Directives.complete(
          StatusCodes.OK,
          Json.obj(
            "owner" -> JsString(extractedOwner.username),
            "scope" -> Json.toJson(extractedScope)
          )
        )
    }

    codes.put(client, code, owner, scope = Some(scope)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  private def createDirective(
    codes: AuthorizationCodeStore
  ) =
    new AuthorizationCodeConsumption {
      override implicit protected def mat: Materializer = ActorMaterializer()
      override implicit protected def ec: ExecutionContext = system.dispatcher
      override protected def log: LoggingAdapter = createLogger()
      override protected def authorizationCodeStore: AuthorizationCodeStore = codes
    }
}
