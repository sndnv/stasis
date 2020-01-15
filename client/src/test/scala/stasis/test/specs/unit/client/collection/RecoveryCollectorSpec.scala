package stasis.test.specs.unit.client.collection

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.collection.RecoveryCollector
import stasis.client.model.{DatasetMetadata, FileMetadata, FilesystemMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.core.packaging.Crate
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}
import stasis.test.specs.unit.client.mocks.MockRecoveryMetadataCollector

import scala.concurrent.Future

class RecoveryCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  "A default RecoveryCollector" should "collect recovery files based on dataset metadata" in {
    val file2Metadata = FileMetadata(
      path = "/collection/file-2".asTestResource,
      size = 0,
      link = None,
      isHidden = false,
      created = Instant.now(),
      updated = Instant.now(),
      owner = "some-owner",
      group = "some-group",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    val file3Metadata = FileMetadata(
      path = "/collection/file-3".asTestResource,
      size = 0,
      link = None,
      isHidden = false,
      created = Instant.now(),
      updated = Instant.now(),
      owner = "some-owner",
      group = "some-group",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    val collector = new RecoveryCollector.Default(
      targetMetadata = DatasetMetadata(
        contentChanged = Map(
          file2Metadata.path -> file2Metadata
        ),
        metadataChanged = Map(
          file3Metadata.path -> file3Metadata
        ),
        filesystem = FilesystemMetadata(
          files = Map(
            file2Metadata.path -> FilesystemMetadata.FileState.New,
            file3Metadata.path -> FilesystemMetadata.FileState.Updated
          )
        )
      ),
      keep = (_, _) => true,
      metadataCollector = new MockRecoveryMetadataCollector(
        metadata = Map(
          file2Metadata.path -> file2Metadata,
          file3Metadata.path -> file3Metadata
        )
      ),
      getMetadataForEntry = _ => Future.failed(new IllegalStateException("Not available"))
    )

    collector
      .collect()
      .runFold(Seq.empty[SourceFile])(_ :+ _)
      .map(_.sortBy(_.path.toAbsolutePath.toString))
      .map {
        case sourceFile2 :: sourceFile3 :: Nil =>
          sourceFile2.path should be(file2Metadata.path)
          sourceFile2.existingMetadata should be(Some(file2Metadata))

          sourceFile3.path should be(file3Metadata.path)
          sourceFile3.existingMetadata should be(Some(file3Metadata))

        case sourceFiles =>
          fail(s"Unexpected number of entries received: [${sourceFiles.size}]")
      }
  }

  it should "collect metadata for individual files (new and updated)" in {
    val targetMetadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      ),
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector
          .collectFileMetadata(
            targetMetadata = targetMetadata,
            keep = (_, _) => true,
            getMetadataForEntry = _ => Future.failed(new IllegalStateException("Not available"))
          )
      )
      .map { actualMetadata =>
        actualMetadata should be(
          Seq(
            Fixtures.Metadata.FileOneMetadata,
            Fixtures.Metadata.FileTwoMetadata
          )
        )
      }
  }

  it should "handle missing metadata when collecting data for individual files (new and updated)" in {
    val targetMetadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector
          .collectFileMetadata(
            targetMetadata = targetMetadata,
            keep = (_, _) => true,
            getMetadataForEntry = _ => Future.failed(new IllegalStateException("Not available"))
          )
          .map(_.failed)
      )
      .map { result =>
        result.map(_.getMessage) should be(
          Seq(
            s"Metadata for file [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] not found",
            s"Metadata for file [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath}] not found"
          )
        )
      }
  }

  it should "collect metadata for individual files (existing)" in {
    val entry = DatasetEntry.generateId()

    val targetMetadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.Existing(entry = entry),
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Existing(entry = entry)
        )
      )
    )

    val entryMetadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      ),
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector
          .collectFileMetadata(
            targetMetadata = targetMetadata,
            keep = (_, _) => true,
            getMetadataForEntry = {
              case `entry` => Future.successful(entryMetadata)
              case other   => Future.failed(new IllegalArgumentException(s"Unexpected entry provided: [$other]"))
            }
          )
      )
      .map { actualMetadata =>
        actualMetadata should be(
          Seq(
            Fixtures.Metadata.FileOneMetadata,
            Fixtures.Metadata.FileTwoMetadata
          )
        )
      }
  }

  it should "handle missing metadata when collecting data for individual files (existing)" in {
    val entry = DatasetEntry.generateId()

    val targetMetadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.Existing(entry = entry),
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Existing(entry = entry)
        )
      )
    )

    val entryMetadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector
          .collectFileMetadata(
            targetMetadata = targetMetadata,
            keep = (_, _) => true,
            getMetadataForEntry = {
              case `entry` => Future.successful(entryMetadata)
              case other   => Future.failed(new IllegalArgumentException(s"Unexpected entry provided: [$other]"))
            }
          )
          .map(_.failed)
      )
      .map { result =>
        result.map(_.getMessage) should be(
          Seq(
            s"Expected metadata for file [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] but none was found in metadata for entry [$entry]",
            s"Expected metadata for file [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath}] but none was found in metadata for entry [$entry]"
          )
        )
      }
  }

  it should "support filtering when collecting data for individual files" in {
    val targetMetadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      ),
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector
          .collectFileMetadata(
            targetMetadata = targetMetadata,
            keep = (_, state) => state == FilesystemMetadata.FileState.New,
            getMetadataForEntry = _ => Future.failed(new IllegalStateException("Not available"))
          )
      )
      .map { actualMetadata =>
        actualMetadata should be(
          Seq(
            Fixtures.Metadata.FileOneMetadata
          )
        )
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DatasetMetadataRecoveryCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)
}
