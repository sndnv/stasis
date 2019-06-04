package stasis.test.specs.unit.client.collection

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.collection.FileMetadataRecoveryCollector
import stasis.client.model.{DatasetMetadata, FileMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class FileMetadataRecoveryCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "FileMetadataRecoveryCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  "A FileMetadataRecoveryCollector" should "collect recovery files based on file metadata" in {
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

    val collector = new FileMetadataRecoveryCollector(
      filesMetadata = Seq(file2Metadata, file3Metadata),
      metadata = new Metadata.Default(
        checksum = Checksum.MD5,
        lastDatasetMetadata = DatasetMetadata.empty
      )
    )

    collector
      .collect()
      .runFold(Seq.empty[SourceFile])(_ :+ _)
      .map(_.sortBy(_.path.toAbsolutePath.toString))
      .map {
        case sourceFile2 :: sourceFile3 :: Nil =>
          sourceFile2.path should be(file2Metadata.path)
          sourceFile2.existingMetadata should be(Some(file2Metadata))
          sourceFile2.currentMetadata.size should be(2)

          sourceFile3.path should be(file3Metadata.path)
          sourceFile3.existingMetadata should be(Some(file3Metadata))
          sourceFile3.currentMetadata.size should be(3)

        case sourceFiles =>
          fail(s"Unexpected number of entries received: [${sourceFiles.size}]")
      }
  }
}
