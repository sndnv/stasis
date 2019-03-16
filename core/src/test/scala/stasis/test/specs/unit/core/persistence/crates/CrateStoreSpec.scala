package stasis.test.specs.unit.core.persistence.crates

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.packaging
import stasis.core.packaging.Crate.Id
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.exceptions.ReservationFailure
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockReservationStore}

import scala.concurrent.Future
import scala.concurrent.duration._

class CrateStoreSpec extends AsyncUnitSpec {

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
      extends CrateStore(reservationStore, reservationExpiration, storeId = Node.generateId())(typedSystem.toUntyped) {
    override protected def directSink(manifest: packaging.Manifest): Future[Sink[ByteString, Future[Done]]] =
      backingCrateStore.sink(manifest)

    override protected def directSource(crate: Id): Future[Option[Source[ByteString, NotUsed]]] =
      backingCrateStore.retrieve(crate)

    override protected def dropContent(crate: Id): Future[Boolean] =
      backingCrateStore.discard(crate)

    override protected def isStorageAvailable(request: CrateStorageRequest): Future[Boolean] =
      Future.successful(isStorageAvailable)
  }

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  "A CrateStore" should "successfully reserve crate storage" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(
      reservationRequest,
      target = store.storeId,
      expiration = 1.second
    )

    val actualReservation = store.reserve(reservationRequest).await match {
      case Some(reservation) => reservation
      case None              => fail("Unexpected reservation response returned")
    }

    actualReservation should be(expectedReservation.copy(id = actualReservation.id))
  }

  it should "fail to reserve crate storage if reservation already exists" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(
      reservationRequest,
      target = store.storeId,
      expiration = 1.second
    )

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
    val expectedReservation = CrateStorageReservation(
      reservationRequest,
      target = store.storeId,
      expiration = 1.second
    )

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
          message should be(s"Failed to remove reservation for crate [${testManifest.crate}]")
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "create stream sinks for persisting crates" in {
    val store = new TestCrateStore()

    val reservationRequest = CrateStorageRequest(testManifest)
    val expectedReservation = CrateStorageReservation(
      reservationRequest,
      target = store.storeId,
      expiration = 1.second
    )

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
          message should be(s"Failed to remove reservation for crate [${testManifest.crate}]")
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(0)
          store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
      }
  }

  it should "successfully retrieve crates" in {
    val store = new TestCrateStore()

    store.backingCrateStore.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

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

  it should "successfully discard existing crates" in {
    val store = new TestCrateStore()

    store.backingCrateStore.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

    store.discard(testManifest.crate).await should be(true)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
  }

  it should "fail to discard crates that do not exist" in {
    val store = new TestCrateStore()

    store.discard(testManifest.crate).await should be(false)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(1)
  }

  it should "provide a read-only view" in {
    val store = new TestCrateStore()
    val storeView = store.view

    store.backingCrateStore.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

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

  it should "create crate stores" in {
    val store = CrateStore(
      backend = StreamingMemoryBackend(maxSize = 1000, name = "streaming-map-store"),
      reservationStore = new MockReservationStore(),
      reservationExpiration = 3.seconds,
      storeId = Node.generateId()
    )(system.toUntyped)

    val request = CrateStorageRequest(
      crate = Crate.generateId(),
      size = Long.MaxValue,
      copies = 1,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    for {
      sink <- store.sink(testManifest)
      source <- store.retrieve(testManifest.crate)
      discarded <- store.discard(testManifest.crate)
      reservation <- store.reserve(request)
    } yield {
      sink shouldBe a[Sink[_, _]]
      source should be(None)
      discarded should be(false)
      reservation should be(None)
    }
  }
}
