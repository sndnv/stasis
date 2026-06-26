package stasis.test.specs.unit.client.ops.backup

import java.nio.file.Files

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup

import stasis.client.collection.BackupCollector
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityRef
import stasis.client.model.SourceEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.BackupEntityKind
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.EntityDiscovery
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers

class BackupEntityKindSpec extends AsyncUnitSpec with ResourceHelpers {
  "A filesystem BackupEntityKind" should "read entity content" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    val file = filesystem.getPath("/file")
    Files.write(file, ByteString("test").toArray)

    val entity = SourceEntity(
      ref = EntityRef.Filesystem(file),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    BackupEntityKind.Filesystem
      .read(entity = entity, ref = EntityRef.Filesystem(file), chunkSize = 8192)
      .runFold(ByteString.empty)(_ ++ _)
      .map(_ should be(ByteString("test")))
  }

  "A BackupEntityKind dispatcher" should "route reads to the filesystem kind" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    val file = filesystem.getPath("/file")
    Files.write(file, ByteString("test").toArray)

    val entity = SourceEntity(
      ref = EntityRef.Filesystem(file),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    BackupEntityKind
      .read(kinds = Seq(BackupEntityKind.Filesystem), entity = entity, chunkSize = 8192)
      .runFold(ByteString.empty)(_ ++ _)
      .map(_ should be(ByteString("test")))
  }

  it should "route reads to a matching library kind" in {
    val entity = SourceEntity(
      ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic"),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    BackupEntityKind
      .read(kinds = Seq(BackupEntityKind.Filesystem, libraryKind), entity = entity, chunkSize = 8192)
      .runFold(ByteString.empty)(_ ++ _)
      .map(_ should be(ByteString("library")))
  }

  it should "fail when no kind is registered for a filesystem ref" in {
    val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    val entity = SourceEntity(
      ref = EntityRef.Filesystem(filesystem.getPath("/file")),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    BackupEntityKind
      .read(kinds = Seq.empty, entity = entity, chunkSize = 8192)
      .runFold(ByteString.empty)(_ ++ _)
      .failed
      .map(_.getMessage should be("No filesystem backup kind was registered"))
  }

  it should "fail when no kind is registered for a library scheme" in {
    val entity = SourceEntity(
      ref = EntityRef.Library(scheme = "unknown", path = "/x"),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    BackupEntityKind
      .read(kinds = Seq(BackupEntityKind.Filesystem), entity = entity, chunkSize = 8192)
      .runFold(ByteString.empty)(_ ++ _)
      .failed
      .map(_.getMessage should be("No backup kind was registered for scheme [unknown]"))
  }

  private val libraryKind = new BackupEntityKind.Library {
    override def scheme: String = "photos"

    override def collector(
      collector: EntityDiscovery.Collector,
      latestMetadata: Option[DatasetMetadata],
      providers: Providers,
      parallelism: ParallelismConfig
    )(implicit operation: Operation.Id, mat: Materializer): Future[BackupCollector] = ???

    override def read(entity: SourceEntity, ref: EntityRef.Library, chunkSize: Int): Source[ByteString, NotUsed] =
      Source.single(ByteString("library"))
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, "BackupEntityKindSpec")
  private implicit val mat: Materializer = Materializer(system)
}
