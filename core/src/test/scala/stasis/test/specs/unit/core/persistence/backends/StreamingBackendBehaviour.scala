package stasis.test.specs.unit.core.persistence.backends

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.core.persistence.backends.StreamingBackend
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.Future

trait StreamingBackendBehaviour { _: AsyncUnitSpec =>
  protected implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    guardianBehavior = Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = "StreamingBackendBehaviour"
  )

  def streamingBackend[B <: StreamingBackend](
    createBackend: () => B,
    before: B => Future[Done] = (backend: B) => backend.init(),
    after: B => Future[Done] = (backend: B) => backend.drop()
  ): Unit = {
    val testKey = java.util.UUID.randomUUID()
    val testContent = ByteString("test-value")

    it should "successfully stream data" in {
      val store = createBackend()

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
      }
    }

    it should "fail to create a stream source if data is missing" in {
      val store = createBackend()

      for {
        _ <- before(store)
        source <- store.source(key = testKey)
        _ <- after(store)
      } yield {
        source should be(None)
      }
    }

    it should "delete data" in {
      val store = createBackend()

      for {
        _ <- before(store)
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        existingSource <- store.source(key = testKey)
        deleteResult <- store.delete(key = testKey)
        missingSource <- store.source(key = testKey)
        _ <- after(store)
      } yield {
        existingSource.isDefined should be(true)
        deleteResult should be(true)
        missingSource.isDefined should be(false)
      }
    }

    it should "check if values exist" in {
      val store = createBackend()

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
      }
    }

    it should "check if data can be stored" in {
      val store = createBackend()

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
      }
    }

    it should "reset itself" in {
      val store = createBackend()

      for {
        _ <- before(store)
        sink <- store.sink(key = testKey)
        _ <- Source.single(testContent).runWith(sink)
        existsBeforeReset <- store.contains(key = testKey)
        _ <- store.drop()
        existsAfterReset <- store.contains(key = testKey)
        _ <- after(store)
      } yield {
        existsBeforeReset should be(true)
        existsAfterReset should be(false)
      }
    }
  }
}
