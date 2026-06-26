package stasis.test.specs.unit.client.ops.recovery

import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup

import stasis.client.analysis.Checksum
import stasis.client.analysis.PlatformMetadata
import stasis.client.api.clients.Clients
import stasis.client.collection.RecoveryCollector
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityRef
import stasis.client.model.FilesystemMetadata
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.RecoveryEntityKind
import stasis.client.staging.DefaultFileStaging
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

class RecoveryEntityKindSpec extends AsyncUnitSpec with ResourceHelpers {
  "A filesystem RecoveryEntityKind" should "prepare and write entity content to its destination" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    val targetPath = filesystem.getPath("/nested/file.txt")

    val entity = TargetEntity(
      ref = EntityRef.Filesystem(targetPath),
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileOneMetadata.copy(path = targetPath.toString),
      currentMetadata = None
    )

    val providers = createProviders(filesystem)
    val ref = EntityRef.Filesystem(targetPath)

    RecoveryEntityKind.Filesystem.prepare(entity = entity, ref = ref, providers = providers)
    Files.isDirectory(targetPath.getParent) should be(true)

    RecoveryEntityKind.Filesystem
      .write(entity = entity, ref = ref, content = Source.single(ByteString("hello")), providers = providers)
      .map { _ =>
        ByteString(Files.readAllBytes(targetPath)) should be(ByteString("hello"))
      }
  }

  "A RecoveryEntityKind dispatcher" should "route prepare to a matching library kind" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)
    val kind = new MockLibraryKind

    RecoveryEntityKind.prepare(kinds = Seq(kind), entity = libraryEntity("photos"), providers = createProviders(filesystem))

    kind.prepared.get() should be(true)
  }

  it should "skip prepare when no kind is registered for a library scheme" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    noException should be thrownBy RecoveryEntityKind.prepare(
      kinds = Seq(RecoveryEntityKind.Filesystem),
      entity = libraryEntity("unknown"),
      providers = createProviders(filesystem)
    )
  }

  it should "fail to write when no kind is registered for a filesystem ref" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    RecoveryEntityKind
      .write(
        kinds = Seq.empty,
        entity = filesystemEntity,
        content = Source.single(ByteString("x")),
        providers = createProviders(filesystem)
      )
      .failed
      .map(_.getMessage should be("No filesystem recovery kind was registered"))
  }

  it should "route writes to a matching library kind" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    RecoveryEntityKind
      .write(
        kinds = Seq(new MockLibraryKind),
        entity = libraryEntity("photos"),
        content = Source.single(ByteString("x")),
        providers = createProviders(filesystem)
      )
      .map(_ should be(Done))
  }

  it should "fail to write when no kind is registered for a library scheme" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    RecoveryEntityKind
      .write(
        kinds = Seq(RecoveryEntityKind.Filesystem),
        entity = libraryEntity("unknown"),
        content = Source.single(ByteString("x")),
        providers = createProviders(filesystem)
      )
      .failed
      .map(_.getMessage should be("No recovery kind was registered for scheme [unknown]"))
  }

  it should "fail to apply metadata when no kind is registered for a filesystem ref" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    RecoveryEntityKind
      .applyMetadata(kinds = Seq.empty, entity = filesystemEntity, providers = createProviders(filesystem))
      .failed
      .map(_.getMessage should be("No filesystem recovery kind was registered"))
  }

  it should "route apply metadata to a matching library kind" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    RecoveryEntityKind
      .applyMetadata(kinds = Seq(new MockLibraryKind), entity = libraryEntity("photos"), providers = createProviders(filesystem))
      .map(_ should be(Done))
  }

  it should "fail to apply metadata when no kind is registered for a library scheme" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    RecoveryEntityKind
      .applyMetadata(
        kinds = Seq(RecoveryEntityKind.Filesystem),
        entity = libraryEntity("unknown"),
        providers = createProviders(filesystem)
      )
      .failed
      .map(_.getMessage should be("No recovery kind was registered for scheme [unknown]"))
  }

  private def libraryEntity(scheme: String): TargetEntity =
    TargetEntity(
      ref = EntityRef.Library(scheme = scheme, path = "/album/img.heic"),
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None
    )

  private val filesystemEntity: TargetEntity =
    TargetEntity(
      ref = EntityRef.Filesystem(Fixtures.Metadata.FileOneMetadata.path.asPath),
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None
    )

  private class MockLibraryKind extends RecoveryEntityKind.Library {
    val prepared: AtomicBoolean = new AtomicBoolean(false)

    override def scheme: String = "photos"

    override def collector(
      targetMetadata: DatasetMetadata,
      keep: (String, FilesystemMetadata.EntityState) => Boolean,
      destination: TargetEntity.Destination,
      providers: Providers,
      parallelism: ParallelismConfig
    )(implicit mat: Materializer): RecoveryCollector = ???

    override def prepare(entity: TargetEntity, ref: EntityRef.Library, providers: Providers): Unit = {
      val _ = prepared.set(true)
    }

    override def write(entity: TargetEntity, ref: EntityRef.Library, content: Source[ByteString, NotUsed], providers: Providers)(
      implicit mat: Materializer
    ): Future[Done] = Future.successful(Done)

    override def applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: Providers)(implicit
      ec: ExecutionContext
    ): Future[Done] = Future.successful(Done)
  }

  private def createProviders(filesystem: FileSystem): Providers = {
    val stagingDirectory: Path = Files.createDirectories(filesystem.getPath("/staging"))

    Providers(
      checksum = Checksum.MD5,
      staging = new DefaultFileStaging(storeDirectory = Some(stagingDirectory), prefix = "staged-", suffix = ".tmp"),
      compression = MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
      track = new MockRecoveryTracker(),
      telemetry = MockClientTelemetryContext(),
      filesystem = filesystem,
      metadataDefaults = PlatformMetadata.Defaults.default(),
      kinds = Seq(RecoveryEntityKind.Filesystem)
    )
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, "RecoveryEntityKindSpec")
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: Materializer = Materializer(system)
}
