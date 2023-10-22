package stasis.test.specs.unit.core.persistence.backends.file.state

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.persistence.backends.file.state.StateStore
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.FileSystemHelpers

import scala.concurrent.Future
import scala.util.{Success, Try}

trait StateStoreBehaviour { _: AsyncUnitSpec with FileSystemHelpers =>
  import FileSystemHelpers._
  import StateStoreBehaviour._

  def stateStore(setup: FileSystemSetup)(implicit system: ActorSystem[SpawnProtocol.Command]): Unit = {
    it should "support persisting state to file" in withRetry {
      val (filesystem, _) = createMockFileSystem(setup)

      val target = filesystem.getPath("/store")

      val store = StateStore[Map[String, State]](
        directory = target.toString,
        retainedVersions = 10,
        filesystem = filesystem
      )

      val state = Map(
        "id-a" -> State(a = "a", b = 1, c = true),
        "id-b" -> State(a = "b", b = 2, c = false),
        "id-c" -> State(a = "c", b = 3, c = true)
      )

      store
        .persist(state = state)
        .map { _ =>
          target.files().toList match {
            case persistedStatePath :: Nil =>
              val content = persistedStatePath.content.await
              val deserialized = serdes.deserialize(content.getBytes)
              deserialized should be(Success(state))

            case other =>
              fail(s"Unexpected result received: [$other]")
          }
        }
    }

    it should "support pruning old state files" in withRetry {
      val (filesystem, _) = createMockFileSystem(setup)

      val target = filesystem.getPath("/store")

      val store = new StateStore[Map[String, State]](
        directory = target.toString,
        retainedVersions = 10,
        filesystem = filesystem
      )

      val initialState = Map.empty[String, State]

      val updatedState = Map(
        "id-a" -> State(a = "a", b = 1, c = true)
      )

      val latestState = Map(
        "id-a" -> State(a = "a", b = 1, c = true),
        "id-b" -> State(a = "b", b = 2, c = false),
        "id-c" -> State(a = "c", b = 3, c = true)
      )

      for {
        _ <- store.persist(state = initialState)
        _ <- store.persist(state = updatedState)
        _ <- store.persist(state = latestState)
        filesBeforePruning = target.files().toList
        stateBeforePruning <- Future.sequence(filesBeforePruning.map(_.content))
        _ <- store.prune(keep = 1)
        filesAfterPruning = target.files().toList
        stateAfterPruning <- Future.sequence(filesAfterPruning.map(_.content))
      } yield {
        filesBeforePruning.length should be(3)
        filesAfterPruning.length should be(1)

        stateBeforePruning.map(_.getBytes).map(serdes.deserialize) match {
          case actualInitialState :: actualUpdatedState :: actualLatestState :: Nil =>
            actualInitialState should be(Success(initialState))
            actualUpdatedState should be(Success(updatedState))
            actualLatestState should be(Success(latestState))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        stateAfterPruning.map(_.getBytes).map(serdes.deserialize) match {
          case actualLatestState :: Nil =>
            actualLatestState should be(Success(latestState))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }

    it should "support discarding state files" in withRetry {
      val (filesystem, _) = createMockFileSystem(setup)

      val target = filesystem.getPath("/store")

      val store = new StateStore[Map[String, State]](
        directory = target.toString,
        retainedVersions = 10,
        filesystem = filesystem
      )

      val initialState = Map.empty[String, State]

      val updatedState = Map(
        "id-a" -> State(a = "a", b = 1, c = true)
      )

      val latestState = Map(
        "id-a" -> State(a = "a", b = 1, c = true),
        "id-b" -> State(a = "b", b = 2, c = false),
        "id-c" -> State(a = "c", b = 3, c = true)
      )

      for {
        _ <- store.persist(state = initialState)
        _ <- store.persist(state = updatedState)
        _ <- store.persist(state = latestState)
        filesBeforePruning = target.files().toList
        stateBeforePruning <- Future.sequence(filesBeforePruning.map(_.content))
        _ <- store.discard()
        filesAfterPruning = target.files().toList
      } yield {
        filesBeforePruning.length should be(3)
        filesAfterPruning.length should be(0)

        stateBeforePruning.map(_.getBytes).map(serdes.deserialize) match {
          case actualInitialState :: actualUpdatedState :: actualLatestState :: Nil =>
            actualInitialState should be(Success(initialState))
            actualUpdatedState should be(Success(updatedState))
            actualLatestState should be(Success(latestState))

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }

    it should "support restoring existing state from file" in withRetry {
      val (filesystem, _) = createMockFileSystem(setup)

      val target = filesystem.getPath("/store")

      val store = StateStore[Map[String, State]](
        directory = target.toString,
        retainedVersions = 10,
        filesystem = filesystem
      )

      val state = Map(
        "id-a" -> State(a = "a", b = 1, c = true),
        "id-b" -> State(a = "b", b = 2, c = false),
        "id-c" -> State(a = "c", b = 3, c = true)
      )

      for {
        _ <- store.persist(state = state)
        restoredState <- store.restore()
      } yield {
        restoredState should be(Some(state))
      }
    }

    it should "handle deserialization failures" in withRetry {
      val (filesystem, _) = createMockFileSystem(setup)

      val target = filesystem.getPath("/store")

      val store = new StateStore[Map[String, State]](
        directory = target.toString,
        retainedVersions = 10,
        filesystem = filesystem
      )

      val initialState = Map.empty[String, State]

      val updatedState = Map(
        "id-a" -> State(a = "a", b = 1, c = true)
      )

      val latestState = Map(
        "id-a" -> State(a = "a", b = 1, c = true),
        "id-b" -> State(a = "b", b = 2, c = false),
        "id-c" -> State(a = "c", b = 3, c = true)
      )

      for {
        _ <- store.persist(state = initialState)
        _ <- store.persist(state = updatedState)
        _ <- store.persist(state = latestState)
        _ = target.files().lastOption.foreach(_.write("invalid").await)
        restoredState <- store.restore()
        _ = target.files().foreach(_.write("invalid").await)
        emptyState <- store.restore()
      } yield {
        restoredState should be(Some(updatedState))
        emptyState should be(None)
      }
    }
  }

  private implicit val serdes: StateStore.Serdes[Map[String, State]] =
    new StateStore.Serdes[Map[String, State]] {
      override def serialize(state: Map[String, State]): Array[Byte] =
        state
          .map { case (k, v) => s"$k->${v.productIterator.map(_.toString).mkString(",")}" }
          .mkString(";")
          .getBytes

      override def deserialize(bytes: Array[Byte]): Try[Map[String, State]] =
        Try(
          new String(bytes)
            .split(";")
            .map(_.trim)
            .filter(_.nonEmpty)
            .map { entry =>
              entry.split("->").toList match {
                case key :: value :: Nil =>
                  val state = value.split(",").toList match {
                    case a :: b :: c :: Nil => State(a = a, b = b.toInt, c = c.toBoolean)
                    case other              => fail(s"Unexpected result received: [$other]")
                  }

                  key -> state

                case other =>
                  fail(s"Unexpected result received: [$other]")
              }
            }
            .toMap
        )
    }
}

object StateStoreBehaviour {
  private final case class State(a: String, b: Int, c: Boolean)
}
