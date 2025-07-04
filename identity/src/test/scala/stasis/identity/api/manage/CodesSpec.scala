package stasis.identity.api.manage

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.StatusCodes

import stasis.identity.RouteTest
import stasis.identity.api.manage.CodesSpec.PartialStoredAuthorizationCode
import stasis.identity.model.Generators
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import io.github.sndnv.layers

class CodesSpec extends RouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "Codes routes" should "respond with all codes" in withRetry {
    val store = createCodeStore()
    val codes = new Codes(store)

    val owner = Generators.generateResourceOwner
    val expectedCodes = layers.testing.Generators.generateSeq(min = 2, g = Generators.generateAuthorizationCode)

    Future
      .sequence(
        expectedCodes.map(code => store.put(StoredAuthorizationCode(code, Client.generateId(), owner, scope = None)))
      )
      .await
    Get() ~> codes.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[PartialStoredAuthorizationCode]].sortBy(_.code) should be(
        expectedCodes
          .map(code => PartialStoredAuthorizationCode(code.value, owner.username, scope = None))
          .sortBy(_.code)
      )
    }
  }

  they should "respond with existing authorization codes for clients" in withRetry {
    val store = createCodeStore()
    val codes = new Codes(store)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode

    store.put(StoredAuthorizationCode(code, client, owner, scope = None)).await
    Get(s"/${code.value}") ~> codes.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[PartialStoredAuthorizationCode] should be(
        PartialStoredAuthorizationCode(code.value, owner.username, scope = None)
      )
    }
  }

  they should "fail if no authorization codes are available for a client" in withRetry {
    val store = createCodeStore()
    val codes = new Codes(store)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode

    store.put(StoredAuthorizationCode(code, Client.generateId(), owner, scope = None)).await
    Get(s"/${Generators.generateAuthorizationCode.value}") ~> codes.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing authorization codes" in withRetry {
    val store = createCodeStore()
    val codes = new Codes(store)

    val client = Client.generateId()
    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode

    store.put(StoredAuthorizationCode(code, client, owner, scope = None)).await
    Delete(s"/${code.value}") ~> codes.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.all.await.size should be(0)
    }
  }

  they should "not delete missing authorization codes" in withRetry {
    val store = createCodeStore()
    val codes = new Codes(store)

    Delete(s"/${Generators.generateAuthorizationCode.value}") ~> codes.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private val user = "some-user"
}

object CodesSpec {
  import play.api.libs.json._

  implicit val partialStoredAuthorizationCodeReads: Reads[PartialStoredAuthorizationCode] =
    Json.reads[PartialStoredAuthorizationCode]

  final case class PartialStoredAuthorizationCode(
    code: String,
    owner: String,
    scope: Option[String]
  )
}
