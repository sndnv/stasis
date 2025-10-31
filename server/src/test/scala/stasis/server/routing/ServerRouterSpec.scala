package stasis.server.routing

import scala.concurrent.Future
import scala.concurrent.duration._

import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import org.scalatest.concurrent.Eventually

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Node
import stasis.core.routing.Router
import stasis.server.events.Events
import stasis.server.events.mocks.MockEventCollector

class ServerRouterSpec extends UnitSpec with Eventually {
  "A ServerRouter" should "provide events for push operations" in withRetry {
    implicit val collector: MockEventCollector = MockEventCollector()
    val underlying = createMockRouter()
    val router = ServerRouter(underlying = underlying)

    collector.events should be(empty)

    val manifest = Manifest(
      crate = Crate.generateId(),
      origin = Node.generateId(),
      source = Node.generateId(),
      size = 1,
      copies = 1
    )

    router.push(manifest = manifest, content = Source.empty).await

    eventually {
      collector.events.toList match {
        case event :: Nil =>
          event.name should be("crate_pushed")
          event.attributes should be(
            Map(
              Events.Crates.Attributes.Crate.withValue(manifest.crate),
              Events.Crates.Attributes.Manifest.withValue(manifest)
            )
          )

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "provide no events for pull operations" in withRetry {
    implicit val collector: MockEventCollector = MockEventCollector()
    val underlying = createMockRouter()
    val router = ServerRouter(underlying = underlying)

    collector.events should be(empty)

    router.pull(crate = Crate.generateId()).await

    collector.events should be(empty)
  }

  it should "provide events for discard operations" in withRetry {
    implicit val collector: MockEventCollector = MockEventCollector()
    val underlying = createMockRouter()
    val router = ServerRouter(underlying = underlying)

    collector.events should be(empty)

    val crate = Crate.generateId()

    router.discard(crate = crate).await

    eventually {
      collector.events.toList match {
        case event :: Nil =>
          event.name should be("crate_discarded")
          event.attributes should be(
            Map(
              Events.Crates.Attributes.Crate.withValue(crate)
            )
          )

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "provide no events for reserve operations" in withRetry {
    implicit val collector: MockEventCollector = MockEventCollector()
    val underlying = createMockRouter()
    val router = ServerRouter(underlying = underlying)

    collector.events should be(empty)

    val request = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 1,
      copies = 1,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    router.reserve(request = request).await

    collector.events should be(empty)
  }

  private def createMockRouter(): Router = new Router {
    override def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
      Future.successful(Done)

    override def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
      Future.successful(None)

    override def discard(crate: Crate.Id): Future[Done] =
      Future.successful(Done)

    override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
      Future.successful(None)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  override implicit val timeout: Timeout = 5.seconds
}
