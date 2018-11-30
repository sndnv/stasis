package stasis.test.specs.unit.routing

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import org.scalatest.FutureOutcome
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import stasis.packaging.{Crate, Manifest}
import stasis.routing.{LocalRouter, Node}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.mocks.MockStore

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class LocalRouterSpec extends AsyncUnitSpec with Eventually with ScalaFutures {

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest): FutureOutcome =
    withFixture(test.toNoArgAsyncTest(FixtureParam()))

  override implicit val timeout: Timeout = 500.milliseconds
  private implicit val system: ActorSystem = ActorSystem()
  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContext = system.dispatcher

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3.second, 250.milliseconds)

  private val store = new MockStore
  private val router = new LocalRouter(store)

  "A LocalRouter" should "push data to a local store" in { _ =>
    val manifest = Manifest(
      crate = Crate.generateId(),
      copies = 1,
      retention = 60.seconds,
      source = Node(id = Node.generateId())
    )

    val content = ByteString("some value")

    router.push(manifest, Source.single(content))

    eventually {
      val result = store
        .retrieve(manifest.crate)
        .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
        .await

      result should be(content)
    }
  }

  it should "pull data from a local store" in { _ =>
    val manifest = Manifest(
      crate = Crate.generateId(),
      copies = 1,
      retention = 60.seconds,
      source = Node(id = Node.generateId())
    )

    val content = ByteString("some other value")

    store.persist(manifest, Source.single(content))

    eventually {
      val result = router
        .pull(manifest.crate)
        .flatMap(_.getOrElse(Source.empty).runFold(ByteString.empty)(_ ++ _))
        .await

      result should be(content)
    }
  }

  it should "fail to pull missing data from a local store" in { _ =>
    router.pull(Crate.generateId()).map(_ should be(None))
  }
}
