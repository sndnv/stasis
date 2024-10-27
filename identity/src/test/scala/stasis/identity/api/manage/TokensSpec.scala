package stasis.identity.api.manage

import org.apache.pekko.http.scaladsl.model.StatusCodes

import stasis.identity.RouteTest
import stasis.identity.api.manage.TokensSpec.PartialStoredRefreshToken
import stasis.identity.model.Generators
import stasis.identity.model.clients.Client
import stasis.identity.model.tokens.RefreshToken
import stasis.layers

class TokensSpec extends RouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "Tokens routes" should "respond with all refresh tokens" in withRetry {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val owner = Generators.generateResourceOwner
    val expectedTokens = layers.Generators
      .generateSeq(min = 2, g = Generators.generateRefreshToken)
      .map(token => PartialStoredRefreshToken(token.value, Client.generateId(), owner.username, scope = None))

    expectedTokens.foreach(token => store.put(token.client, RefreshToken(token.token), owner, token.scope).await)
    Get() ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[PartialStoredRefreshToken]].sortBy(_.token) should be(expectedTokens.sortBy(_.token))
    }
  }

  they should "respond with existing refresh tokens for clients" in withRetry {
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

  they should "fail if no refresh tokens are available for a client" in withRetry {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken

    store.put(Client.generateId(), token, owner, scope = None).await
    Get(s"/${Generators.generateRefreshToken.value}") ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing refresh tokens" in withRetry {
    val store = createTokenStore()
    val tokens = new Tokens(store)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val token = Generators.generateRefreshToken

    store.put(client, token, owner, scope = None).await
    Delete(s"/${token.value}") ~> tokens.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.all.await.size should be(0)
    }
  }

  they should "not delete missing refresh tokens" in withRetry {
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
