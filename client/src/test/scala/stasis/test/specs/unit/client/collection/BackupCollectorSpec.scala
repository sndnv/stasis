package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import stasis.client.analysis.Checksum
import stasis.client.collection.{BackupCollector, BackupMetadataCollector}
import stasis.client.model.{DatasetMetadata, EntityMetadata, FilesystemMetadata, SourceEntity}
import stasis.client.ops.ParallelismConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.{MockCompression, MockServerApiEndpointClient}
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

import scala.concurrent.Future

class BackupCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  "A default BackupCollector" should "collect backup files based on a files list" in {
    val mockApiClient = MockServerApiEndpointClient()

    val file1 = "/collection/file-1".asTestResource
    val file2 = "/collection/file-2".asTestResource

    val collector = new BackupCollector.Default(
      entities = List(file1, file2),
      latestMetadata = Some(DatasetMetadata.empty),
      metadataCollector = new BackupMetadataCollector.Default(checksum = Checksum.MD5, compression = MockCompression()),
      api = mockApiClient
    )

    collector
      .collect()
      .runFold(Seq.empty[SourceEntity])(_ :+ _)
      .map(_.sortBy(_.path.toAbsolutePath.toString))
      .map {
        case sourceFile1 :: sourceFile2 :: Nil =>
          sourceFile1.path should be(file1)
          sourceFile1.existingMetadata should be(None)
          sourceFile1.currentMetadata match {
            case metadata: EntityMetadata.File => metadata.size should be(1)
            case _: EntityMetadata.Directory   => fail("Expected file but received directory metadata")
          }

          sourceFile2.path should be(file2)
          sourceFile2.existingMetadata should be(None)
          sourceFile2.currentMetadata match {
            case metadata: EntityMetadata.File => metadata.size should be(2)
            case _: EntityMetadata.Directory   => fail("Expected file but received directory metadata")
          }

        case sourceFiles =>
          fail(s"Unexpected number of entries received: [${sourceFiles.size}]")
      }
  }

  it should "collect metadata for individual files" in {
    val mockApiClient = MockServerApiEndpointClient()

    val file1 = "/collection/file-1".asTestResource
    val file1Metadata = Fixtures.Metadata.FileOneMetadata.copy(path = file1)

    val file2 = "/collection/file-2".asTestResource

    Future
      .traverse(
        BackupCollector
          .collectEntityMetadata(
            entities = List(file1, file2),
            latestMetadata = Some(
              DatasetMetadata(
                contentChanged = Map(file1 -> file1Metadata),
                metadataChanged = Map.empty,
                filesystem = FilesystemMetadata(entities = Map(file1 -> FilesystemMetadata.EntityState.New))
              )
            ),
            api = mockApiClient
          )
      ) { case (file, metadataFuture) => metadataFuture.map(metadata => (file, metadata)) }
      .map(_.toList)
      .map {
        case (collectedFile1, collectedFile1Metadata) :: (collectedFile2, collectedFile2Metadata) :: Nil =>
          collectedFile1 should be(file1)
          collectedFile1Metadata should be(Some(file1Metadata))

          collectedFile2 should be(file2)
          collectedFile2Metadata should be(None)

        case collectedFiles =>
          fail(s"Unexpected number of entries received: [${collectedFiles.size}]")
      }
  }

  it should "collect no file metadata if dataset metadata is not available" in {
    val mockApiClient = MockServerApiEndpointClient()

    val file1 = "/collection/file-1".asTestResource
    val file2 = "/collection/file-2".asTestResource

    Future
      .traverse(
        BackupCollector
          .collectEntityMetadata(
            entities = List(file1, file2),
            latestMetadata = None,
            api = mockApiClient
          )
      ) { case (file, metadataFuture) => metadataFuture.map(metadata => (file, metadata)) }
      .map(_.toList)
      .map {
        case (collectedFile1, collectedFile1Metadata) :: (collectedFile2, collectedFile2Metadata) :: Nil =>
          collectedFile1 should be(file1)
          collectedFile1Metadata should be(None)

          collectedFile2 should be(file2)
          collectedFile2Metadata should be(None)

        case collectedFiles =>
          fail(s"Unexpected number of entries received: [${collectedFiles.size}]")
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "BackupCollectorSpec")

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)
}
