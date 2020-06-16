package stasis.test.specs.unit.core.persistence.crates

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.{ContainerBackend, FileBackend}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockCrateStore

import scala.concurrent.Future

class CrateStoreSpec extends AsyncUnitSpec {
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

  it should "successfully persist crates" in {
    val store = new TestCrateStore()

    store.persist(testManifest, Source.single(testContent)).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
  }

  it should "create stream sinks for persisting crates" in {
    val store = new TestCrateStore()

    val sink = store.sink(testManifest.crate).await
    Source.single(testContent).runWith(sink).await

    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistCompleted) should be(1)
    store.backingCrateStore.statistics(MockCrateStore.Statistic.PersistFailed) should be(0)
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

  it should "report storage availability" in {
    val availableStore = new TestCrateStore(isStorageAvailable = true)
    val unavailableStore = new TestCrateStore(isStorageAvailable = false)

    for {
      availableResult <- availableStore.canStore(Generators.generateRequest)
      unavailableResult <- unavailableStore.canStore(Generators.generateRequest)
    } yield {
      availableResult should be(true)
      unavailableResult should be(false)
    }
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

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "CrateStoreSpec"
  )

  private class TestCrateStore(
    val isStorageAvailable: Boolean = true,
    val backingCrateStore: MockCrateStore = new MockCrateStore()
  ) extends CrateStore(
        backend = new StreamingBackend {
          override def init(): Future[Done] = Future.successful(Done)

          override def drop(): Future[Done] = Future.successful(Done)

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

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size.toLong,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )
}
