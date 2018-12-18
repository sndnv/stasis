package stasis.test.specs.unit.persistence.crates

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.scalatest.concurrent.Eventually
import stasis.packaging
import stasis.packaging.{Crate, Manifest}
import stasis.packaging.Crate.Id
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.persistence.crates.CrateStore
import stasis.persistence.exceptions.ReservationFailure
import stasis.persistence.reservations.ReservationStore
import stasis.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.mocks.{MockCrateStore, MockReservationStore}

import scala.concurrent.Future
import scala.concurrent.duration._

class CrateStoreSpec extends AsyncUnitSpec with Eventually {

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "CrateStoreSpec"
  )

  private implicit val mat: ActorMaterializer = ActorMaterializer()(system.toUntyped)

  private class TestCrateStore(
    val reservationStore: ReservationStore = new MockReservationStore(ignoreMissingReservations = false),
    val reservationExpiration: FiniteDuration = 1.second,
    val isStorageAvailable: Boolean = true,
    val backingCrateStore: MockCrateStore = new MockCrateStore(new MockReservationStore())
  )(implicit val typedSystem: ActorSystem[SpawnProtocol])
      extends CrateStore(reservationStore, reservationExpiration)(typedSystem.toUntyped) {
    override protected def directSink(manifest: packaging.Manifest): Future[Sink[ByteString, Future[Done]]] =
      backingCrateStore.sink(manifest)

    override protected def directSource(crate: Id): Future[Option[Source[ByteString, NotUsed]]] =
      backingCrateStore.retrieve(crate)

    override protected def isStorageAvailable(request: CrateStorageRequest): Future[Boolean] =
      Future.successful(isStorageAvailable)
  }

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size,
    copies = 4,
    retention = 60.seconds,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  "A CrateStore" should "successfully reserve crate storage" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(reservationRequest, expiration = 1.second)
    val actualReservation = store.reserve(reservationRequest).await match {
      case Some(reservation) => reservation
      case None              => fail("Unexpected reservation response returned")
    }

    actualReservation should be(expectedReservation.copy(id = actualReservation.id))
  }

  it should "fail to reserve crate storage if reservation already exists" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(reservationRequest, expiration = 1.second)
    val actualReservation = store.reserve(reservationRequest).await match {
      case Some(reservation) => reservation
      case None              => fail("Unexpected reservation response returned")
    }

    actualReservation should be(expectedReservation.copy(id = actualReservation.id))

    store
      .reserve(reservationRequest)
      .map { response =>
        fail(s"Received unexpected push response from store: [$response]")
      }
      .recover {
        case ReservationFailure(message) =>
          message should be(
            s"Failed to process reservation request [$reservationRequest]; " +
              s"reservation already exists for crate [${reservationRequest.crate}]"
          )
      }
  }

  it should "fail to reserve if not enough storage is available" in {
    val store = new TestCrateStore(isStorageAvailable = false)
    store.reserve(CrateStorageRequest(testManifest)).await should be(None)
  }

  it should "successfully persist crates" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(reservationRequest, expiration = 1.second)
    val actualReservation = store.reserve(reservationRequest).await match {
      case Some(reservation) => reservation
      case None              => fail("Unexpected reservation response returned")
    }

    actualReservation should be(expectedReservation.copy(id = actualReservation.id))

    store.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
  }

  it should "fail to persist crates if no reservation is available" in {
    val store = new TestCrateStore()

    store
      .persist(testManifest, Source.single(testContent))
      .map { response =>
        fail(s"Received unexpected push response from store: [$response]")
      }
      .recover {
        case ReservationFailure(message) =>
          message should be(s"Failed to discard reservation for crate [${testManifest.crate}]")
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "create stream sinks for persisting crates" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(reservationRequest, expiration = 1.second)
    val actualReservation = store.reserve(reservationRequest).await match {
      case Some(reservation) => reservation
      case None              => fail("Unexpected reservation response returned")
    }

    actualReservation should be(expectedReservation.copy(id = actualReservation.id))

    val sink = store.sink(testManifest).await
    Source.single(testContent).runWith(sink).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
  }

  it should "fail to create stream sink if no reservation is available" in {
    val store = new TestCrateStore()

    store
      .sink(testManifest)
      .map { response =>
        fail(s"Received unexpected push response from store: [$response]")
      }
      .recover {
        case ReservationFailure(message) =>
          message should be(s"Failed to discard reservation for crate [${testManifest.crate}]")
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "successfully retrieve crates" in {
    val store = new TestCrateStore()

    store.backingCrateStore.persist(testManifest, Source.single(testContent)).await

    eventually {
      store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }

    val actualSource = store.retrieve(testManifest.crate).await match {
      case Some(source) => source
      case None         => fail("Unexpected retrieve response returned")
    }

    val result = actualSource
      .runFold(ByteString.empty) { case (folded, chunk) => folded.concat(chunk) }
      .await

    result should be(testContent)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)
  }

  it should "provide a read-only view" in {
    val store = new TestCrateStore()
    val storeView = store.view

    store.backingCrateStore.persist(testManifest, Source.single(testContent)).await

    eventually {
      store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
      store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
    }

    val actualSource = storeView.retrieve(testManifest.crate).await match {
      case Some(source) => source
      case None         => fail("Unexpected retrieve response returned")
    }

    val result = actualSource
      .runFold(ByteString.empty) { case (folded, chunk) => folded.concat(chunk) }
      .await

    result should be(testContent)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.RetrieveCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.RetrieveEmpty) should be(0)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.RetrieveFailed) should be(0)

    a[ClassCastException] should be thrownBy {
      storeView.asInstanceOf[CrateStore]
    }
  }
}
