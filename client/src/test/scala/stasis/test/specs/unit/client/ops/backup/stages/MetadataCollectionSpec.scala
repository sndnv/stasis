package stasis.test.specs.unit.client.ops.backup.stages

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.model.DatasetMetadata
import stasis.client.model.FilesystemMetadata
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.MetadataCollection
import stasis.client.tracking.BackupTracker
import stasis.client.tracking.state.BackupState
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class MetadataCollectionSpec extends AsyncUnitSpec {
  "A Backup MetadataCollection stage" should "collect dataset metadata (with previous metadata)" in {
    val mockTracker = new MockBackupTracker

    val metadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        changes = Seq(
          Fixtures.Metadata.FileOneMetadata.path
        )
      )
    )

    val stage = new MetadataCollection {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def latestEntry: Option[DatasetEntry] = Some(Fixtures.Entries.Default)
      override protected def latestMetadata: Option[DatasetMetadata] = Some(metadata)
      override protected def providers: Providers = createProviders(mockTracker)
    }

    val stageInput = List(
      Right(Fixtures.Metadata.FileOneMetadata), // metadata changed
      Left(Fixtures.Metadata.FileTwoMetadata), // content changed
      Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
    )

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(stageInput)
      .via(stage.metadataCollection(existingState = None))
      .runFold(Seq.empty[DatasetMetadata])(_ :+ _)
      .map {
        case metadata :: Nil =>
          metadata should be(
            DatasetMetadata(
              contentChanged = Map(
                Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
              ),
              metadataChanged = Map(
                Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata,
                Fixtures.Metadata.FileThreeMetadata.path -> Fixtures.Metadata.FileThreeMetadata
              ),
              filesystem = FilesystemMetadata(
                entities = Map(
                  Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.Updated,
                  Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.New,
                  Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.New
                )
              )
            )
          )

          mockTracker.statistics(MockBackupTracker.Statistic.Started) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntitySkipped) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

        case metadata =>
          fail(s"Unexpected number of entries received: [${metadata.size}]")
      }
  }

  it should "collect dataset metadata (without previous metadata)" in {
    val mockTracker = new MockBackupTracker

    val stage = new MetadataCollection {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def latestEntry: Option[DatasetEntry] = None
      override protected def latestMetadata: Option[DatasetMetadata] = None
      override protected def providers: Providers = createProviders(mockTracker)
    }

    val stageInput = List(
      Right(Fixtures.Metadata.FileOneMetadata), // metadata changed
      Left(Fixtures.Metadata.FileTwoMetadata), // content changed
      Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
    )

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(stageInput)
      .via(stage.metadataCollection(existingState = None))
      .runFold(Seq.empty[DatasetMetadata])(_ :+ _)
      .map {
        case metadata :: Nil =>
          metadata should be(
            DatasetMetadata(
              contentChanged = Map(
                Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
              ),
              metadataChanged = Map(
                Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata,
                Fixtures.Metadata.FileThreeMetadata.path -> Fixtures.Metadata.FileThreeMetadata
              ),
              filesystem = FilesystemMetadata(
                changes = Seq(
                  Fixtures.Metadata.FileOneMetadata.path,
                  Fixtures.Metadata.FileTwoMetadata.path,
                  Fixtures.Metadata.FileThreeMetadata.path
                )
              )
            )
          )

          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntitySkipped) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

        case metadata =>
          fail(s"Unexpected number of entries received: [${metadata.size}]")
      }
  }

  it should "collect dataset metadata (with existing state)" in {
    val mockTracker = new MockBackupTracker

    val stage = new MetadataCollection {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default

      override protected def latestEntry: Option[DatasetEntry] = None

      override protected def latestMetadata: Option[DatasetMetadata] = None

      override protected def providers: Providers = createProviders(mockTracker)
    }

    val stageInput = List(
      Right(Fixtures.Metadata.FileOneMetadata), // metadata changed
      Left(Fixtures.Metadata.FileTwoMetadata) // content changed
    )

    implicit val operationId: Operation.Id = Operation.generateId()

    val existingState = Fixtures.State.BackupOneState
      .copy(
        entities = Fixtures.State.BackupOneState.entities.copy(
          processed = Map(
            Fixtures.Metadata.FileThreeMetadata.path -> BackupState.ProcessedSourceEntity(
              expectedParts = 1,
              processedParts = 1,
              metadata = Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
            )
          )
        )
      )

    Source(stageInput)
      .via(stage.metadataCollection(existingState = Some(existingState)))
      .runFold(Seq.empty[DatasetMetadata])(_ :+ _)
      .map {
        case metadata :: Nil =>
          metadata should be(
            DatasetMetadata(
              contentChanged = Map(
                Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
              ),
              metadataChanged = Map(
                Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata,
                Fixtures.Metadata.FileThreeMetadata.path -> Fixtures.Metadata.FileThreeMetadata
              ),
              filesystem = FilesystemMetadata(
                changes = Seq(
                  Fixtures.Metadata.FileOneMetadata.path,
                  Fixtures.Metadata.FileTwoMetadata.path,
                  Fixtures.Metadata.FileThreeMetadata.path
                )
              )
            )
          )

          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntitySkipped) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

        case metadata =>
          fail(s"Unexpected number of entries received: [${metadata.size}]")
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataCollectionSpec")

  private def createProviders(tracker: BackupTracker): Providers =
    Providers(
      checksum = Checksum.MD5,
      staging = new MockFileStaging(),
      compression = MockCompression(),
      encryptor = new MockEncryption(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = tracker,
      telemetry = MockClientTelemetryContext()
    )
}
