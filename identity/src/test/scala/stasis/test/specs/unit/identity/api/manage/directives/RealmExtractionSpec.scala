package stasis.test.specs.unit.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import stasis.identity.api.Formats._
import stasis.identity.api.manage.directives.RealmExtraction
import stasis.identity.model.realms.{Realm, RealmStore, RealmStoreView}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class RealmExtractionSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "A RealmExtraction directive" should "extract existing realms" in {
    val store = createRealmStore()
    val directive = createDirective(store)
    val realm = Generators.generateRealm

    val routes = directive.extractRealm(realmId = realm.id) { extractedRealm =>
      Directives.complete(StatusCodes.OK, extractedRealm)
    }

    store.put(realm).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Realm] should be(realm)
    }
  }

  it should "fail to extract missing realms" in {
    val store = createRealmStore()
    val directive = createDirective(store)
    val realm = Generators.generateRealm

    val routes = directive.extractRealm(realmId = realm.id) { extractedRealm =>
      Directives.complete(StatusCodes.OK, extractedRealm.toString)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private def createDirective(store: RealmStore) = new RealmExtraction {
    override implicit protected def mat: Materializer = ActorMaterializer()
    override protected def log: LoggingAdapter = createLogger()
    override protected def realmStore: RealmStoreView = store.view
  }
}
