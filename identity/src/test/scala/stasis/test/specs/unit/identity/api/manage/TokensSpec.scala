package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.StatusCodes
import stasis.identity.api.Formats._
import stasis.identity.api.manage.Tokens
import stasis.identity.model.clients.Client
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.TokensSpec.PartialStoredRefreshToken
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future

class TokensSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Tokens routes" should "respond with all refresh tokens" in {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val expectedTokens = Generators.generateSeq(min = 2, g = Generators.generateRefreshToken)

    Future.sequence(expectedTokens.map(token => store.put(client, token, owner, scope = None))).await
    Get() ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[(String, PartialStoredRefreshToken)]].map(_._2).sortBy(_.token) should be(
        expectedTokens
          .map(token => PartialStoredRefreshToken(token.value, client, owner.username, scope = None))
          .sortBy(_.token))
    }
  }

  they should "respond with existing refresh tokens for clients" in {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken

    store.put(client, token, owner, scope = None).await
    Get(s"/${token.value}") ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[PartialStoredRefreshToken] should be(
        PartialStoredRefreshToken(token.value, client, owner.username, scope = None)
      )
    }
  }

  they should "fail if no refresh tokens are available for a client" in {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken

    store.put(Client.generateId(), token, owner, scope = None).await
    Get(s"/${Generators.generateRefreshToken.value}") ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing refresh tokens" in {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken

    store.put(client, token, owner, scope = None).await
    Delete(s"/${token.value}") ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.tokens.await should be(Map.empty)
    }
  }

  they should "not delete missing refresh tokens" in {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    Delete(s"/${Generators.generateRefreshToken.value}") ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private val user = "some-user"
}

object TokensSpec {
  import play.api.libs.json._

  implicit val partialStoredRefreshTokenReads: Reads[PartialStoredRefreshToken] =
    Json.reads[PartialStoredRefreshToken]

  final case class PartialStoredRefreshToken(
    token: String,
    client: Client.Id,
    owner: String,
    scope: Option[String]
  )
}
