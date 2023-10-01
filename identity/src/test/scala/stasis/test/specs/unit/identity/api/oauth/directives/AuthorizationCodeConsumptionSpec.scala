package stasis.test.specs.unit.identity.api.oauth.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import org.slf4j.Logger
import play.api.libs.json._
import stasis.identity.api.oauth.directives.AuthorizationCodeConsumption
import stasis.identity.api.oauth.directives.AuthorizationCodeConsumption.ChallengeVerification
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import java.security.MessageDigest
import java.util.Base64
import scala.concurrent.ExecutionContext

class AuthorizationCodeConsumptionSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "An AuthorizationCodeConsumption directive" should "consume valid authorization codes without challenges" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      val fields = responseAs[JsObject].fields
      fields should contain("owner" -> Json.toJson(owner.username))
      fields should contain("scope" -> Json.toJson(scope))
      codes.codes.await should be(Map.empty)
    }
  }

  it should "fail if the provided authorization code has an unexpected associated challenge" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"
    val challenge = StoredAuthorizationCode.Challenge(
      value = stasis.test.Generators.generateString(withSize = 128),
      method = None
    )

    val routes = directive.consumeAuthorizationCode(client, code) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope), challenge = Some(challenge))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "consume valid authorization codes with challenges (plain)" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"
    val verifier = stasis.test.Generators.generateString(withSize = 128)
    val challenge = StoredAuthorizationCode.Challenge(value = verifier, method = Some(ChallengeMethod.Plain))

    val routes = directive.consumeAuthorizationCode(client, code, verifier) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope), challenge = Some(challenge))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      val fields = responseAs[JsObject].fields
      fields should contain("owner" -> Json.toJson(owner.username))
      fields should contain("scope" -> Json.toJson(scope))
      codes.codes.await should be(Map.empty)
    }
  }

  it should "consume valid authorization codes with challenges (S256)" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"
    val verifier = stasis.test.Generators.generateString(withSize = 128)

    val encodedVerifier =
      Base64.getUrlEncoder
        .withoutPadding()
        .encodeToString(
          MessageDigest
            .getInstance(ChallengeVerification.DigestAlgorithm)
            .digest(verifier.getBytes(ChallengeVerification.Charset))
        )

    val challenge = StoredAuthorizationCode.Challenge(value = encodedVerifier, method = Some(ChallengeMethod.S256))

    val routes = directive.consumeAuthorizationCode(client, code, verifier) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope), challenge = Some(challenge))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      val fields = responseAs[JsObject].fields
      fields should contain("owner" -> Json.toJson(owner.username))
      fields should contain("scope" -> Json.toJson(scope))
      codes.codes.await should be(Map.empty)
    }
  }

  it should "fail if the provided verifier did not match the challenge of the authorization code" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"
    val challenge = StoredAuthorizationCode.Challenge(
      value = stasis.test.Generators.generateString(withSize = 128),
      method = None
    )

    val routes = directive.consumeAuthorizationCode(
      client = client,
      providedCode = code,
      stasis.test.Generators.generateString(withSize = 128)
    ) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope), challenge = Some(challenge))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "fail if the provided authorization code expected a challenge but none was provided" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"
    val verifier = stasis.test.Generators.generateString(withSize = 128)

    val routes = directive.consumeAuthorizationCode(client, code, verifier) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope), challenge = None)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "fail if the provided and found authorization codes do not match" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) { case (_, _) =>
      Directives.complete(StatusCodes.OK)
    }

    codes.put(StoredAuthorizationCode(Generators.generateAuthorizationCode, client, owner, scope = Some(scope))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "fail if the provided and found authorization code clients do not match" in withRetry {
    val codes = createCodeStore()
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) { case (_, _) =>
      Directives.complete(StatusCodes.OK)
    }

    codes.put(StoredAuthorizationCode(code, Client.generateId(), owner, scope = Some(scope))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_grant"))
    }
  }

  it should "fail if authorization codes could not be queried" in withRetry {
    val codes = createFailingCodeStore(failingGet = true)
    val directive = createDirective(codes)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val scope = "some-scope"

    val routes = directive.consumeAuthorizationCode(client, code) { case (extractedOwner, extractedScope) =>
      Directives.complete(
        StatusCodes.OK,
        Json.obj(
          "owner" -> Json.toJson(extractedOwner.username),
          "scope" -> Json.toJson(extractedScope)
        )
      )
    }

    codes.put(StoredAuthorizationCode(code, client, owner, scope = Some(scope))).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  private def createDirective(
    codes: AuthorizationCodeStore
  ) =
    new AuthorizationCodeConsumption {
      override implicit protected def ec: ExecutionContext = system.dispatcher
      override protected def log: Logger = createLogger()
      override protected def authorizationCodeStore: AuthorizationCodeStore = codes
    }
}
