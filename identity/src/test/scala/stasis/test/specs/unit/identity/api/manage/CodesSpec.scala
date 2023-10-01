package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.StatusCodes
import stasis.identity.api.Formats._
import stasis.identity.api.manage.Codes
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.CodesSpec.PartialStoredAuthorizationCode
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future

class CodesSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Codes routes" should "respond with all codes" in withRetry {
    val store = createCodeStore()
    val codes = new Codes(store)

    val owner = Generators.generateResourceOwner
    val expectedCodes = stasis.test.Generators.generateSeq(min = 2, g = Generators.generateAuthorizationCode)

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
      store.codes.await should be(Map.empty)
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
