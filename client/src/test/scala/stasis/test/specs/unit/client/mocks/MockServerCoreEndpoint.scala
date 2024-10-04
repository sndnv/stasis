package stasis.test.specs.unit.client.mocks

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.networking.http.HttpEndpoint
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.routing.Node
import stasis.layers.security.tls.EndpointContext
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.core.persistence.mocks.MockCrateStore
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockHttpAuthenticator

class MockServerCoreEndpoint(
  expectedCredentials: BasicHttpCredentials
)(implicit typedSystem: ActorSystem[Nothing], telemetry: TelemetryContext) {
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
