package stasis.test.specs.unit.client.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.http.HttpEndpoint
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node
import stasis.core.security.tls.EndpointContext
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockReservationStore}
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockHttpAuthenticator

import scala.concurrent.{ExecutionContext, Future}

class MockServerCoreEndpoint(
  expectedCredentials: BasicHttpCredentials
)(implicit typedSystem: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext) {
  private implicit val ec: ExecutionContext = typedSystem.executionContext

  private val reservationStore = new MockReservationStore()
  private val crateStore = new MockCrateStore()
  private val router = new MockRouter(crateStore, Node.generateId(), reservationStore)

  private val endpoint = new HttpEndpoint(
    router = router,
    authenticator = new MockHttpAuthenticator(expectedCredentials.username, expectedCredentials.password),
    reservationStore = reservationStore.view
  )

  def insertCrate(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    crateStore.persist(manifest, content)

  def crateExists(entry: Crate.Id): Future[Boolean] =
    crateStore.retrieve(entry).map(_.isDefined)

  def start(port: Int, context: Option[EndpointContext] = None): Future[Http.ServerBinding] =
    endpoint.start(
      interface = "localhost",
      port = port,
      context = context
    )
}
