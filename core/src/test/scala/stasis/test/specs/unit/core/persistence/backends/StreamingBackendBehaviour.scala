package stasis.test.specs.unit.core.persistence.backends

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.Future

trait StreamingBackendBehaviour { _: AsyncUnitSpec =>
  protected implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    guardianBehavior = Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = "StreamingBackendBehaviour"
  )

  def streamingBackend[B <: StreamingBackend](
    createBackend: TelemetryContext => B,
    before: B => Future[Done] = (backend: B) => backend.init(),
    after: B => Future[Done] = (backend: B) => backend.drop(),
    alwaysAvailable: Boolean = false
  ): Unit = {
    val testKey = java.util.UUID.randomUUID()
    val testContent = ByteString("test-value")

    it should "successfully stream data" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        source <- store.source(key = testKey).map {
          case Some(source) => source
          case None         => fail("Failed to retrieve source")
        }
        result <- source.runFold(ByteString.empty) { case (folded, chunk) =>
          folded.concat(chunk)
        }
        _ <- after(store)
      } yield {
        result should be(testContent)

        telemetry.persistence.streaming.init should be(1)
        telemetry.persistence.streaming.drop should be(1)
        telemetry.persistence.streaming.write should be(1)
        telemetry.persistence.streaming.read should be(1)
        telemetry.persistence.streaming.discard should be(0)
      }
    }

    it should "fail to create a stream source if data is missing" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        source <- store.source(key = testKey).consume()
        _ <- after(store)
      } yield {
        source should be(None)

        telemetry.persistence.streaming.init should be(1)
        telemetry.persistence.streaming.drop should be(1)
        telemetry.persistence.streaming.write should be(0)
        telemetry.persistence.streaming.read should be(0)
        telemetry.persistence.streaming.discard should be(0)
      }
    }

    it should "delete data" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        existingSource <- store.source(key = testKey).consume()
        deleteResult <- store.delete(key = testKey)
        missingSource <- store.source(key = testKey).consume()
        _ <- after(store)
      } yield {
        existingSource.isDefined should be(true)
        deleteResult should be(true)
        missingSource.isDefined should be(false)

        telemetry.persistence.streaming.init should be(1)
        telemetry.persistence.streaming.drop should be(1)
        telemetry.persistence.streaming.write should be(1)
        telemetry.persistence.streaming.read should be(1)
        telemetry.persistence.streaming.discard should be(1)
      }
    }

    it should "check if values exist" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        existsBeforePush <- store.contains(key = testKey)
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        existsAfterPush <- store.contains(key = testKey)
        _ <- store.delete(key = testKey)
        existsAfterDelete <- store.contains(key = testKey)
        _ <- after(store)
      } yield {
        existsBeforePush should be(false)
        existsAfterPush should be(true)
        existsAfterDelete should be(false)

        telemetry.persistence.streaming.init should be(1)
        telemetry.persistence.streaming.drop should be(1)
        telemetry.persistence.streaming.write should be(1)
        telemetry.persistence.streaming.read should be(0)
        telemetry.persistence.streaming.discard should be(1)
      }
    }

    it should "check if data can be stored" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        canStore <- store.canStore(bytes = 100)
        cantStore <- store.canStore(bytes = Long.MaxValue)
        _ <- after(store)
      } yield {
        canStore should be(true)
        cantStore should be(false)

        telemetry.persistence.streaming.init should be(1)
        telemetry.persistence.streaming.drop should be(1)
        telemetry.persistence.streaming.write should be(1)
        telemetry.persistence.streaming.read should be(0)
        telemetry.persistence.streaming.discard should be(0)
      }
    }

    it should "reset itself" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        availableAfterInit <- store.available()
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        existsBeforeReset <- store.contains(key = testKey)
        _ <- store.drop()
        availableAfterDrop <- store.available()
        existsAfterReset <- store.contains(key = testKey)
        _ <- after(store)
      } yield {
        availableAfterInit should be(true)
        existsBeforeReset should be(true)
        existsAfterReset should be(false)
        availableAfterDrop should be(alwaysAvailable) // either the backend is always available or should have been dropped

        telemetry.persistence.streaming.init should be(1)
        telemetry.persistence.streaming.drop should be(2)
        telemetry.persistence.streaming.write should be(1)
        telemetry.persistence.streaming.read should be(0)
        telemetry.persistence.streaming.discard should be(0)
      }
    }
  }

  private implicit class ExtendedFutureSource(futureSource: Future[Option[Source[ByteString, _]]]) {
    def consume(): Future[Option[Done]] =
      futureSource.flatMap {
        case Some(source) => source.runWith(Sink.ignore).map(Option.apply)
        case None         => Future.successful(None)
      }
  }
}
