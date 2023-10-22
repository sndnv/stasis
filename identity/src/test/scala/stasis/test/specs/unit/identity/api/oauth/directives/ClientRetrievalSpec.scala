package stasis.test.specs.unit.identity.api.oauth.directives

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import org.slf4j.Logger
import stasis.identity.api.oauth.directives.ClientRetrieval
import stasis.identity.model.clients.{ClientStore, ClientStoreView}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class ClientRetrievalSpec extends RouteTest {
  "A ClientRetrieval directive" should "retrieve active clients" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val client = Generators.generateClient

    val routes = directive.retrieveClient(client.id) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(client.id.toString)
    }
  }

  it should "fail if a client is not active" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val client = Generators.generateClient.copy(active = false)

    val routes = directive.retrieveClient(client.id) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be("The request was made by an inactive client")
    }
  }

  it should "fail if a client is not found" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val client = Generators.generateClient

    val routes = directive.retrieveClient(client.id) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        "The request has missing, invalid or mismatching redirection URI and/or client identifier"
      )
    }
  }

  it should "fail if clients could not be queried" in withRetry {
    val clients = createFailingClientStore(failingGet = true)
    val directive = createDirective(clients)

    val client = Generators.generateClient

    val routes = directive.retrieveClient(client.id) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  private def createDirective(
    clients: ClientStore
  ) =
    new ClientRetrieval {
      override protected def log: Logger = createLogger()
      override protected def clientStore: ClientStoreView = clients.view
    }
}
