package stasis.test.specs.unit.client.collection

import java.nio.file.Paths
import java.time.Instant

import org.apache.pekko.actor.ActorSystem
import stasis.client.collection.RecoveryCollector
import stasis.client.model.{DatasetMetadata, EntityMetadata, FilesystemMetadata, TargetEntity}
import stasis.client.ops.ParallelismConfig
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.{MockRecoveryMetadataCollector, MockServerApiEndpointClient}
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

import scala.concurrent.Future

class RecoveryCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  "A default RecoveryCollector" should "collect recovery files based on dataset metadata" in {
    val file2 = "/collection/file-2".asTestResource
    val file3 = "/collection/file-3".asTestResource

    val file2Metadata = EntityMetadata.File(
      path = file2,
      size = 0,
      link = None,
      isHidden = false,
      created = Instant.now(),
      updated = Instant.now(),
      owner = "some-owner",
      group = "some-group",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crates = Map(
        Paths.get(s"${file2}_0") -> Crate.generateId()
      ),
      compression = "none"
    )

    val file3Metadata = EntityMetadata.File(
      path = file3,
      size = 0,
      link = None,
      isHidden = false,
      created = Instant.now(),
      updated = Instant.now(),
      owner = "some-owner",
      group = "some-group",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crates = Map(
        Paths.get(s"${file3}_0") -> Crate.generateId()
      ),
      compression = "none"
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
          entities = Map(
            file2Metadata.path -> FilesystemMetadata.EntityState.New,
            file3Metadata.path -> FilesystemMetadata.EntityState.Updated
          )
        )
      ),
      keep = (_, _) => true,
      destination = TargetEntity.Destination.Default,
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
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
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
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.Updated
        )
      )
    )

    Future
      .sequence(
        RecoveryCollector.collectEntityMetadata(
          targetMetadata = targetMetadata,
          keep = (_, state) => state == FilesystemMetadata.EntityState.New,
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

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
}
