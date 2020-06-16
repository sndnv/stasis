package stasis.test.specs.unit.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.{Materializer, SystemMaterializer}
import play.api.libs.json._
import stasis.identity.api.oauth.directives.AudienceExtraction
import stasis.identity.model.apis.{ApiStore, ApiStoreView}
import stasis.identity.model.clients.{ClientStore, ClientStoreView}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.{ExecutionContext, Future}

class AudienceExtractionSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "An AudienceExtraction directive" should "convert client audience to scopes" in {
    val directive = createDirective(clients = createClientStore(), apis = createApiStore())

    val clients = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)
    val validScope = directive.clientAudienceToScope(clients)
    val missingScope = directive.clientAudienceToScope(audience = Seq.empty)

    validScope.isDefined should be(true)
    missingScope.isDefined should be(false)

    val clientIds = validScope
      .map(_.split(" ").toSeq)
      .getOrElse(Seq.empty)
      .flatMap(_.split(":").lastOption)
      .map(java.util.UUID.fromString)

    clientIds should be(clients.map(_.id))
  }

  it should "convert API audience to scopes" in {
    val directive = createDirective(clients = createClientStore(), apis = createApiStore())

    val apis = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateApi)
    val validScope = directive.apiAudienceToScope(apis)
    val missingScope = directive.apiAudienceToScope(audience = Seq.empty)

    validScope.isDefined should be(true)
    missingScope.isDefined should be(false)

    val apiIds = validScope
      .map(_.split(" ").toSeq)
      .getOrElse(Seq.empty)
      .flatMap(_.split(":").lastOption)

    apiIds should be(apis.map(_.id))
  }

  it should "extract audience from valid client identifiers" in {
    val clientStore = createClientStore()
    val apiStore = createApiStore()
    val directive = createDirective(clients = clientStore, apis = apiStore)

    val clients = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)
    val scope = directive.clientAudienceToScope(clients)

    val routes = directive.extractClientAudience(scopeOpt = scope) { clients =>
      Directives.complete(StatusCodes.OK, clients.map(_.id.toString).mkString(","))
    }

    Future.sequence(clients.map(clientStore.put)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(clients.map(_.id.toString).mkString(","))
    }
  }

  it should "fail to extract audience from invalid client identifiers" in {
    val clientStore = createClientStore()
    val apiStore = createApiStore()
    val directive = createDirective(clients = clientStore, apis = apiStore)

    val invalidScope = s"${AudienceExtraction.UrnPrefix}:some-client"

    val routes = directive.extractClientAudience(scopeOpt = Some(invalidScope)) { _ =>
      Directives.complete(StatusCodes.OK)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_scope"))
    }
  }

  it should "extract audience from API identifiers" in {
    val clientStore = createClientStore()
    val apiStore = createApiStore()
    val directive = createDirective(clients = clientStore, apis = apiStore)

    val apis = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateApi)
    val scope = directive.apiAudienceToScope(apis)

    val routes = directive.extractApiAudience(scopeOpt = scope) { apis =>
      Directives.complete(StatusCodes.OK, apis.map(_.id).mkString(","))
    }

    Future.sequence(apis.map(apiStore.put)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(apis.map(_.id).mkString(","))
    }

  }

  it should "fail to extract audience from missing scopes" in {
    val clientStore = createClientStore()
    val apiStore = createApiStore()
    val directive = createDirective(clients = clientStore, apis = apiStore)

    val routes = directive.extractClientAudience(scopeOpt = None) { clients =>
      Directives.complete(StatusCodes.OK, clients.map(_.id.toString).mkString(","))
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_scope"))
    }
  }

  it should "fail to extract audience from invalid scopes" in {
    val clientStore = createClientStore()
    val apiStore = createApiStore()
    val directive = createDirective(clients = clientStore, apis = apiStore)

    val clients = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)

    val routes = directive.extractClientAudience(scopeOpt = Some("invalid-scope")) { clients =>
      Directives.complete(StatusCodes.OK, clients.map(_.id.toString).mkString(","))
    }

    Future.sequence(clients.map(clientStore.put)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_scope"))
    }
  }

  it should "fail to extract audience when target entity is missing" in {
    val clientStore = createFailingClientStore(failingGet = true)
    val apiStore = createApiStore()
    val directive = createDirective(clients = clientStore, apis = apiStore)

    val clients = stasis.test.Generators.generateSeq(min = 1, g = Generators.generateClient)
    val scope = directive.clientAudienceToScope(clients)

    val routes = directive.extractClientAudience(scopeOpt = scope) { clients =>
      Directives.complete(StatusCodes.OK, clients.map(_.id.toString).mkString(","))
    }

    Future.sequence(clients.map(clientStore.put)).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  private def createDirective(
    clients: ClientStore,
    apis: ApiStore
  ) =
    new AudienceExtraction {
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = system.dispatcher
      override protected def log: LoggingAdapter = createLogger()
      override protected def clientStore: ClientStoreView = clients.view
      override protected def apiStore: ApiStoreView = apis.view
    }
}
