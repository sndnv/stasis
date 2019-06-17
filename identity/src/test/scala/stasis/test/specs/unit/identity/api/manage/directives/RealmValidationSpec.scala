package stasis.test.specs.unit.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import stasis.identity.api.Formats._
import stasis.identity.api.manage.directives.RealmValidation
import stasis.identity.model.apis.Api
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future

class RealmValidationSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "A RealmValidation directive" should "filter entities by realm" in {
    val directive = createDirective()
    val realm = Generators.generateRealm
    val entities = Map(
      "1" -> Api(id = "some-api-1", realm = realm.id),
      "2" -> Api(id = "some-api-2", realm = Generators.generateRealmId),
      "3" -> Api(id = "some-api-3", realm = realm.id)
    )

    val routes = directive.filterRealm(realm.id, Future.successful(entities)) { filteredEntities =>
      Directives.complete(StatusCodes.OK, filteredEntities)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Map[String, Api]] should be(entities.filter(_._2.realm == realm.id))
    }
  }

  it should "validate an existing entity's realm" in {
    val directive = createDirective()
    val realm = Generators.generateRealm
    val entity = Api(id = "some-api", realm = realm.id)

    val routes = directive.validateRealm(realm.id, Future.successful(Some(entity))) { _ =>
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "fail to validate an existing entity in an invalid realm" in {
    val directive = createDirective()
    val realm = Generators.generateRealm
    val entity = Api(id = "some-api", realm = Generators.generateRealmId)

    val routes = directive.validateRealm(realm.id, Future.successful(Some(entity))) { _ =>
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  it should "fail to validate a missing entity's realm" in {
    val directive = createDirective()
    val realm = Generators.generateRealm

    val routes = directive.validateRealm(realm.id, Future.successful(None)) { _ =>
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private def createDirective() = new RealmValidation[Api] {
    override implicit protected def mat: Materializer = ActorMaterializer()
    override protected def log: LoggingAdapter = createLogger()
    override implicit protected def extractor: RealmValidation.Extractor[Api] = api => api.realm
  }
}
