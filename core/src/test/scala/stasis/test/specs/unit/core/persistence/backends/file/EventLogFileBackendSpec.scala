package stasis.test.specs.unit.core.persistence.backends.file

import java.nio.file.Files

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import stasis.core.persistence.backends.file.EventLogFileBackend
import stasis.core.persistence.backends.file.state.StateStore
import stasis.layers.FileSystemHelpers
import stasis.layers.FileSystemHelpers.FileSystemSetup
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.EventLogBackendBehaviour
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class EventLogFileBackendSpec extends AsyncUnitSpec with EventLogBackendBehaviour with Eventually with FileSystemHelpers {
  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "EventLogFileBackendSpec"
  )

  private implicit val serdes: StateStore.Serdes[Queue[String]] =
    new StateStore.Serdes[Queue[String]] {
      override def serialize(state: Queue[String]): Array[Byte] =
        state.mkString(";").getBytes

      override def deserialize(bytes: Array[Byte]): Try[Queue[String]] =
        Try(new String(bytes).split(";").to(Queue))
    }

  "An EventLogPersistenceBackend" should behave like
    eventLogBackend[EventLogFileBackend[String, Queue[String]]](
      createBackend = telemetry => {
        val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

        implicit val t: TelemetryContext = telemetry

        EventLogFileBackend(
          config = EventLogFileBackend.Config(
            name = "log-store",
            persistAfterEvents = 100,
            persistAfterPeriod = 1.minute
          ),
          initialState = Queue.empty[String],
          stateStore = StateStore(
            directory = "/store",
            filesystem = filesystem
          )
        )
      }
    )

  it should "support restoring existing state on start" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expectedState = Queue("a", "b", "c")

    val stateStore = StateStore(
      directory = "/store",
      filesystem = filesystem
    )

    stateStore.persist(state = expectedState).await

    val backend = EventLogFileBackend[String, Queue[String]](
      config = EventLogFileBackend.Config(
        name = "log-store",
        persistAfterEvents = 100,
        persistAfterPeriod = 1.minute
      ),
      initialState = Queue.empty[String],
      stateStore = stateStore
    )

    eventually[Assertion] {
      backend.getState.await should be(expectedState)
    }
  }

  it should "support persisting state if too many events have been cached" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val target = "/store"

    val backend = EventLogFileBackend[String, Queue[String]](
      config = EventLogFileBackend.Config(
        name = "log-store",
        persistAfterEvents = 2,
        persistAfterPeriod = 1.minute
      ),
      initialState = Queue.empty[String],
      stateStore = StateStore(
        directory = target,
        retainedVersions = 10,
        filesystem = filesystem
      )
    )

    val testEvent = "test-event"

    for {
      stateBefore <- backend.getState
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      stateAfter <- backend.getState
    } yield {
      stateBefore should be(empty)
      stateAfter should be(Queue(testEvent, testEvent, testEvent, testEvent, testEvent))

      filesystem.getPath(target).files().size should be(2)
    }
  }

  it should "support persisting state if too much time has passed" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val target = "/store"

    val delay = 100.millis

    val backend = EventLogFileBackend[String, Queue[String]](
      config = EventLogFileBackend.Config(
        name = "log-store",
        persistAfterEvents = 100,
        persistAfterPeriod = delay / 2
      ),
      initialState = Queue.empty[String],
      stateStore = StateStore(
        directory = target,
        retainedVersions = 10,
        filesystem = filesystem
      )
    )

    val testEvent = "test-event"

    for {
      stateBefore <- backend.getState
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- after(delay = delay, using = system)(Future.successful(Done))
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- after(delay = delay, using = system)(Future.successful(Done))
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- after(delay = delay, using = system)(Future.successful(Done))
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- after(delay = delay, using = system)(Future.successful(Done))
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      _ <- after(delay = delay, using = system)(Future.successful(Done))
      stateAfter <- backend.getState
    } yield {
      stateBefore should be(empty)
      stateAfter should be(Queue(testEvent, testEvent, testEvent, testEvent, testEvent))

      filesystem.getPath(target).files().size should be(5)
    }
  }

  it should "support restoring state after it has been persisted" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val target = "/store"

    def createBackend(): EventLogFileBackend[String, Queue[String]] =
      EventLogFileBackend[String, Queue[String]](
        config = EventLogFileBackend.Config(
          name = "log-store",
          persistAfterEvents = 2,
          persistAfterPeriod = 250.millis
        ),
        initialState = Queue.empty[String],
        stateStore = StateStore(
          directory = target,
          filesystem = filesystem
        )
      )

    val testEvent = "test-event"

    val backend = createBackend()

    for {
      stateBefore <- backend.getState
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-0", update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-1", update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-2", update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-3", update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-4", update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-5", update = (event, state) => state :+ event)
      _ <- backend.storeEventAndUpdateState(event = s"$testEvent-6", update = (event, state) => state :+ event)
      stateAfter <- backend.getState
    } yield {
      stateBefore should be(empty)
      stateAfter.length should be(7)

      eventually[Assertion] {
        createBackend().getState.await should be(stateAfter)
      }
    }
  }

  it should "handle state restore failures" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expectedState = Queue("a", "b", "c")

    val stateStore = new StateStore(
      directory = "/store",
      retainedVersions = 10,
      filesystem = filesystem
    ) {
      override def restore(): Future[Option[Queue[String]]] =
        Future.failed(new RuntimeException("Test failure"))
    }

    stateStore.persist(state = expectedState).await

    val backend = EventLogFileBackend[String, Queue[String]](
      config = EventLogFileBackend.Config(
        name = "log-store",
        persistAfterEvents = 100,
        persistAfterPeriod = 1.minute
      ),
      initialState = Queue.empty[String],
      stateStore = stateStore
    )

    backend.getState.map { actualState =>
      actualState should be(empty)
    }
  }

  it should "handle state restore providing no data" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expectedState = Queue("a", "b", "c")

    val target = "/store"

    Files.createDirectories(filesystem.getPath(target))

    val backend = EventLogFileBackend[String, Queue[String]](
      config = EventLogFileBackend.Config(
        name = "log-store",
        persistAfterEvents = 100,
        persistAfterPeriod = 1.minute
      ),
      initialState = expectedState,
      stateStore = StateStore(
        directory = target,
        retainedVersions = 10,
        filesystem = filesystem
      )
    )

    backend.getState.map { actualState =>
      actualState should be(expectedState)
    }
  }

  it should "handle state persistence failures" in withRetry {
    val (filesystem, _) = createMockFileSystem(FileSystemSetup.Unix.withEmptyDirs)

    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val target = "/store"

    val stateStore = new StateStore(
      directory = target,
      retainedVersions = 10,
      filesystem = filesystem
    ) {
      override def persist(state: Queue[String]): Future[Done] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val backend = EventLogFileBackend[String, Queue[String]](
      config = EventLogFileBackend.Config(
        name = "log-store",
        persistAfterEvents = 1,
        persistAfterPeriod = 1.minute
      ),
      initialState = Queue.empty[String],
      stateStore = stateStore
    )

    val testEvent = "test-event"

    for {
      stateBefore <- backend.getState
      _ <- backend.storeEventAndUpdateState(event = testEvent, update = (event, state) => state :+ event)
      stateAfter <- backend.getState
    } yield {
      stateBefore should be(empty)
      stateAfter should be(Queue(testEvent))

      Files.exists(filesystem.getPath(target)) should be(false)
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  override implicit val timeout: Timeout = 10.seconds
}
