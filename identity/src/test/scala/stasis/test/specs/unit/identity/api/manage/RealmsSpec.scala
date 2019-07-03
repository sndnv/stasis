package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.StatusCodes
import stasis.identity.api.Formats._
import stasis.identity.api.manage.Realms
import stasis.identity.api.manage.requests.CreateRealm
import stasis.identity.model.realms.Realm
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future

class RealmsSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Realms routes" should "respond with all realms" in {
    val store = createRealmStore()
    val realms = new Realms(store)

    val expectedRealms = Generators.generateSeq(min = 2, g = Generators.generateRealm)

    Future.sequence(expectedRealms.map(store.put)).await
    Get() ~> realms.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Realm]].sortBy(_.id) should be(expectedRealms.sortBy(_.id))
    }
  }

  they should "create new realms" in {
    val store = createRealmStore()
    val realms = new Realms(store)

    val request = CreateRealm(id = "some-realm", refreshTokensAllowed = true)

    Post().withEntity(request) ~> realms.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(request.id).await should be(
        Some(Realm(id = request.id, refreshTokensAllowed = request.refreshTokensAllowed))
      )
    }
  }

  they should "respond with existing realms" in {
    val store = createRealmStore()
    val realms = new Realms(store)

    val expectedRealm = Generators.generateRealm

    store.put(expectedRealm).await
    Get(s"/${expectedRealm.id}") ~> realms.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Realm] should be(expectedRealm)
    }
  }

  they should "fail if a realm is missing" in {
    val store = createRealmStore()
    val realms = new Realms(store)

    Get(s"/some-realm") ~> realms.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing realms" in {
    val store = createRealmStore()
    val realms = new Realms(store)

    val realm = Generators.generateRealm

    store.put(realm).await
    Delete(s"/${realm.id}") ~> realms.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.realms.await should be(Map.empty)
    }
  }

  they should "not delete missing realms" in {
    val store = createRealmStore()
    val realms = new Realms(store)

    Delete("/some-realm") ~> realms.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private val user = "some-user"
}
