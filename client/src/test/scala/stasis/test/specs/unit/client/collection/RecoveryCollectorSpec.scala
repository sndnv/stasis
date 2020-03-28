package stasis.test.specs.unit.client.collection

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.collection.RecoveryCollector
import stasis.client.model.{DatasetMetadata, FileMetadata, FilesystemMetadata, TargetFile}
import stasis.client.ops.ParallelismConfig
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.{MockRecoveryMetadataCollector, MockServerApiEndpointClient}
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

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
      destination = TargetFile.Destination.Default,
      metadataCollector = new MockRecoveryMetadataCollector(
        metadata = Map(
          file2Metadata.path -> file2Metadata,
          file3Metadata.path -> file3Metadata
        )
      ),
      api = MockServerApiEndpointClient()
    )

    collector
      .collect()
      .runFold(Seq.empty[TargetFile])(_ :+ _)
      .map(_.sortBy(_.path.toAbsolutePath.toString))
      .map {
        case targetFile2 :: targetFile3 :: Nil =>
          targetFile2.path should be(file2Metadata.path)
          targetFile2.existingMetadata should be(file2Metadata)
          targetFile2.currentMetadata should not be empty

          targetFile3.path should be(file3Metadata.path)
          targetFile3.existingMetadata should be(file3Metadata)
          targetFile3.currentMetadata should not be empty

        case targetFiles =>
          fail(s"Unexpected number of entries received: [${targetFiles.size}]")
      }
  }

  it should "collect metadata for individual files" in {
    val targetMetadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata,
        Fixtures.Metadata.FileThreeMetadata.path -> Fixtures.Metadata.FileThreeMetadata
      ),
      filesystem = FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.FileState.Updated,
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector.collectFileMetadata(
          targetMetadata = targetMetadata,
          keep = (_, state) => state == FilesystemMetadata.FileState.New,
          api = MockServerApiEndpointClient()
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

  private implicit val system: ActorSystem = ActorSystem(name = "DatasetMetadataRecoveryCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)
}
