package stasis.test.specs.unit.core.persistence.crates

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.{ContainerBackend, FileBackend}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockCrateStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration._

class CrateStoreSpec extends AsyncUnitSpec with AsyncMockitoSugar with Eventually {
  "A CrateStore" should "create descriptors from config" in {
    val config = ConfigFactory.load().getConfig("stasis.test.core.persistence")

    val expectedMemoryDescriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
      maxSize = 1000,
      maxChunkSize = 2000,
      name = "test-memory-store"
    )
    val actualMemoryDescriptor = CrateStore.Descriptor(
      config = config.getConfig("crate-store-memory")
    )

    actualMemoryDescriptor should be(expectedMemoryDescriptor)

    val expectedContainerDescriptor = CrateStore.Descriptor.ForContainerBackend(
      path = "target/some-container",
      maxChunkSize = 1,
      maxChunks = 10
    )
    val actualContainerDescriptor = CrateStore.Descriptor(
      config = config.getConfig("crate-store-container")
    )

    actualContainerDescriptor should be(expectedContainerDescriptor)

    val expectedFileDescriptor = CrateStore.Descriptor.ForFileBackend(
      parentDirectory = "target/some-directory"
    )
    val actualFileDescriptor = CrateStore.Descriptor(
      config = config.getConfig("crate-store-file")
    )

    actualFileDescriptor should be(expectedFileDescriptor)
  }

  it should "create store from descriptors" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val memoryBackedStore = CrateStore.fromDescriptor(
      descriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
        maxSize = 1,
        maxChunkSize = 8192,
        name = s"memory-backed-store-${java.util.UUID.randomUUID()}"
      )
    )

    memoryBackedStore.backend shouldBe a[StreamingMemoryBackend]

    val containerBackedStore = CrateStore.fromDescriptor(
      descriptor = CrateStore.Descriptor.ForContainerBackend(
        path = s"target/container-backed-store-${java.util.UUID.randomUUID()}",
        maxChunkSize = 1,
        maxChunks = 1
      )
    )

    containerBackedStore.backend shouldBe a[ContainerBackend]

    val fileBackedStore = CrateStore.fromDescriptor(
      descriptor = CrateStore.Descriptor.ForFileBackend(
        parentDirectory = "target/file-backed-store"
      )
    )

    fileBackedStore.backend shouldBe a[FileBackend]
  }

  it should "convert crate store descriptors to strings" in {
    val memoryBackendName = s"memory-backed-store-${java.util.UUID.randomUUID()}"
    CrateStore.Descriptor
      .ForStreamingMemoryBackend(maxSize = 1, maxChunkSize = 2, name = memoryBackendName)
      .toString should be(s"StreamingMemoryBackend(maxSize=1, maxChunkSize=2, name=$memoryBackendName)")

    val containerBackendPath = s"target/container-backed-store-${java.util.UUID.randomUUID()}"
    CrateStore.Descriptor
      .ForContainerBackend(path = containerBackendPath, maxChunkSize = 1, maxChunks = 1)
      .toString should be(s"ContainerBackend(path=$containerBackendPath, maxChunkSize=1, maxChunks=1)")

    val fileBackendParentDirectory = "target/file-backed-store"
    CrateStore.Descriptor
      .ForFileBackend(parentDirectory = fileBackendParentDirectory)
      .toString should be(s"FileBackend(parentDirectory=$fileBackendParentDirectory)")
  }

  it should "support logged initialization of a backend (successful)" in {
    import CrateStore.ExtendedStreamingBackend

    val logger = mock[Logger]
    val captor = ArgCaptor[String]

    val initializedSuccessfulBackend = new AtomicBoolean(false)
    val successfulBackend = new LifecycleTestStreamingBackend(
      initResult = () => Future.successful(Done),
      initialized = initializedSuccessfulBackend
    )

    successfulBackend.loggedInit()(logger, system.executionContext)

    eventually[Assertion] {
      verify(logger).debugN(eqTo("Initializing backend [{}]"), captor.capture)
      verify(logger).debugN(eqTo("Backend [{}] successfully initialized"), captor.capture)

      captor.values.distinct should be(List("LifecycleTestStreamingBackend"))
    }
  }

  it should "support logged initialization of a backend (skipped)" in {
    import CrateStore.ExtendedStreamingBackend

    val logger = mock[Logger]
    val captor = ArgCaptor[String]

    val initializedSuccessfulBackend = new AtomicBoolean(true)
    val successfulBackend = new LifecycleTestStreamingBackend(
      initResult = () => Future.successful(Done),
      initialized = initializedSuccessfulBackend
    )

    successfulBackend.loggedInit()(logger, system.executionContext)

    eventually[Assertion] {
      verify(logger).debugN(eqTo("Skipping initialization of backend [{}]; it already exists"), captor.capture)
      verify(logger).debugN(eqTo("Backend [{}] successfully initialized"), captor.capture)

      captor.values.distinct should be(List("LifecycleTestStreamingBackend"))
    }
  }

  it should "support logged initialization of a backend (failed)" in {
    import CrateStore.ExtendedStreamingBackend

    val logger = mock[Logger]
    val captor = ArgCaptor[String]

    val initializedFailingBackend = new AtomicBoolean(false)
    val failingBackend = new LifecycleTestStreamingBackend(
      initResult = () => Future.failed(new RuntimeException("Test failure")),
      initialized = initializedFailingBackend
    )

    failingBackend.loggedInit()(logger, system.executionContext)

    eventually[Assertion] {
      verify(logger).debugN(
        eqTo("Initializing backend [{}]"),
        captor.capture
      )

      verify(logger).errorN(
        eqTo("Failed to initialize backend [{}]: [{} - {}]"),
        captor.capture,
        captor.capture,
        captor.capture
      )

      captor.values.distinct should be(List("LifecycleTestStreamingBackend", "RuntimeException", "Test failure"))
    }
  }

  it should "support initializing the underlying backend" in {
    val store = new LifecycleTestCrateStore()

    eventually[Assertion] {
      store.initialized.get() should be(true)
    }
  }

  it should "successfully persist crates" in {
    val store = createCrateStore()

    store.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
  }

  it should "create stream sinks for persisting crates" in {
    val store = createCrateStore()

    val sink = store.sink(testManifest.crate).await
    Source.single(testContent).runWith(sink).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
  }

  it should "successfully retrieve crates" in {
    val store = createCrateStore()

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
    val store = createCrateStore()

    store.backingCrateStore.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)

    store.discard(testManifest.crate).await should be(true)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(0)
  }

  it should "fail to discard crates that do not exist" in {
    val store = createCrateStore()

    store.discard(testManifest.crate).await should be(false)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardCompleted) should be(0)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.DiscardFailed) should be(1)
  }

  it should "report storage availability" in {
    val availableStore = createCrateStore()
    val unavailableStore = createCrateStore(isStorageAvailable = false)

    for {
      availableResult <- availableStore.canStore(Generators.generateRequest)
      unavailableResult <- unavailableStore.canStore(Generators.generateRequest)
    } yield {
      availableResult should be(true)
      unavailableResult should be(false)
    }
  }

  it should "provide a read-only view" in {

    val store = createCrateStore()
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

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "CrateStoreSpec"
  )

  private def createCrateStore(
    isStorageAvailable: Boolean = true,
    backingCrateStore: Option[MockCrateStore] = None
  ): TestCrateStore = {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    new TestCrateStore(
      isStorageAvailable = isStorageAvailable,
      backingCrateStore = backingCrateStore.getOrElse(new MockCrateStore())
    )
  }

  private class TestCrateStore(
    val isStorageAvailable: Boolean,
    val backingCrateStore: MockCrateStore
  ) extends CrateStore(
        backend = new StreamingBackend {
          override val info: String = "TestCrateStore"

          override def init(): Future[Done] = Future.successful(Done)

          override def drop(): Future[Done] = Future.successful(Done)

          override def available(): Future[Boolean] = Future.successful(true)

          override def sink(key: Crate.Id): Future[Sink[ByteString, Future[Done]]] =
            backingCrateStore.sink(key)

          override def source(key: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
            backingCrateStore.retrieve(key)

          override def delete(key: Crate.Id): Future[Boolean] =
            backingCrateStore.discard(key)

          override def contains(key: Crate.Id): Future[Boolean] =
            Future.successful(false)

          override def canStore(bytes: Long): Future[Boolean] =
            Future.successful(isStorageAvailable)
        }
      )

  private class LifecycleTestCrateStore(
    val initialized: AtomicBoolean = new AtomicBoolean(false)
  ) extends CrateStore(
        backend = new LifecycleTestStreamingBackend(
          initResult = () => Future.successful(Done),
          initialized = initialized
        )
      )

  private class LifecycleTestStreamingBackend(
    initResult: () => Future[Done],
    initialized: AtomicBoolean
  ) extends StreamingBackend {
    override val info: String = "LifecycleTestStreamingBackend"

    override def init(): Future[Done] = {
      initialized.set(true)
      initResult()
    }

    override def available(): Future[Boolean] =
      Future.successful(initialized.get)

    override def drop(): Future[Done] =
      Future.successful(Done)

    override def sink(key: UUID): Future[Sink[ByteString, Future[Done]]] =
      Future.failed(new UnsupportedOperationException())

    override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] =
      Future.failed(new UnsupportedOperationException())

    override def delete(key: UUID): Future[Boolean] =
      Future.failed(new UnsupportedOperationException())

    override def contains(key: UUID): Future[Boolean] =
      Future.failed(new UnsupportedOperationException())

    override def canStore(bytes: Long): Future[Boolean] =
      Future.failed(new UnsupportedOperationException())
  }

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size.toLong,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
