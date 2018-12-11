package stasis.test.specs.unit.routing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import org.scalatest.FutureOutcome
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.routing.{LocalRouter, Node}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.mocks.{MockCrateStore, MockReservationStore}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class LocalRouterSpec extends AsyncUnitSpec with Eventually with ScalaFutures {

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest): FutureOutcome =
    withFixture(test.toNoArgAsyncTest(FixtureParam()))

  override implicit val timeout: Timeout = 500.milliseconds

  private implicit val untypedSystem: akka.actor.ActorSystem = akka.actor.ActorSystem("LocalRouterSpec_Untyped")

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "LocalRouterSpec_Typed"
  )

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = untypedSystem.dispatcher

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3.second, 250.milliseconds)

  "A LocalRouter" should "push data to a local store" in { _ =>
    val crateStore = new MockCrateStore(new MockReservationStore())
    val router = new LocalRouter(crateStore)

    val content = ByteString("some value")

    val manifest = Manifest(
      crate = Crate.generateId(),
      size = content.size,
      copies = 1,
      retention = 60.seconds,
      source = Node.generateId()
    )

    router.push(manifest, Source.single(content)).await

    eventually {
      val result = crateStore
        .retrieve(manifest.crate)
        .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
        .await

      result should be(content)
    }
  }

  it should "pull data from a local store" in { _ =>
    val crateStore = new MockCrateStore(new MockReservationStore())
    val router = new LocalRouter(crateStore)

    val content = ByteString("some other value")

    val manifest = Manifest(
      crate = Crate.generateId(),
      size = content.size,
      copies = 1,
      retention = 60.seconds,
      source = Node.generateId()
    )

    crateStore.persist(manifest, Source.single(content)).await

    eventually {
      val result = router
        .pull(manifest.crate)
        .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
        .await

      result should be(content)
    }
  }

  it should "fail to pull missing data from a local store" in { _ =>
    val crateStore = new MockCrateStore(new MockReservationStore())
    val router = new LocalRouter(crateStore)

    router.pull(Crate.generateId()).map(_ should be(None))
  }

  it should "forward reservation requests to the underlying crate store" in { _ =>
    val crateStore = new MockCrateStore(new MockReservationStore(), maxReservationSize = Some(99))
    val router = new LocalRouter(crateStore)

    val request = CrateStorageRequest(
      id = CrateStorageRequest.generateId(),
      size = 1,
      copies = 1,
      retention = 1.second
    )

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      size = request.size,
      copies = request.copies,
      retention = request.retention,
      expiration = 1.day
    )

    for {
      reservationUnderLimit <- router.reserve(request)
      reservationOverLimit <- router.reserve(request.copy(size = 100))
    } yield {
      reservationUnderLimit should be(
        Some(expectedReservation.copy(id = reservationUnderLimit.map(_.id).getOrElse(expectedReservation.id)))
      )

      reservationOverLimit should be(None)
    }
  }
}
