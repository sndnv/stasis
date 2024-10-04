package stasis.test.specs.unit.client.ops

import java.nio.file.Paths

import stasis.client.model.SourceEntity
import stasis.client.model.TargetEntity
import stasis.client.ops.Metrics
import stasis.layers.telemetry.mocks.MockMeter
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(
      Set(
        Metrics.BackupOperation.NoOp,
        Metrics.RecoveryOperation.NoOp
      )
    )

    val backupMetrics = Metrics.BackupOperation.NoOp
    noException should be thrownBy backupMetrics.recordEntityExamined(entity = null)
    noException should be thrownBy backupMetrics.recordEntitySkipped(entity = null)
    noException should be thrownBy backupMetrics.recordEntityCollected(entity = null)
    noException should be thrownBy backupMetrics.recordEntityChunkProcessed(step = null, bytes = 0)
    noException should be thrownBy backupMetrics.recordEntityChunkProcessed(step = null, extra = null, bytes = 0)
    noException should be thrownBy backupMetrics.recordEntityProcessed(metadata = null)

    val recoveryMetrics = Metrics.RecoveryOperation.NoOp
    noException should be thrownBy recoveryMetrics.recordEntityExamined(entity = null)
    noException should be thrownBy recoveryMetrics.recordEntityCollected(entity = null)
    noException should be thrownBy recoveryMetrics.recordEntityChunkProcessed(step = null, bytes = 0)
    noException should be thrownBy recoveryMetrics.recordEntityChunkProcessed(step = null, extra = null, bytes = 0)
    noException should be thrownBy recoveryMetrics.recordEntityProcessed(entity = null)
    noException should be thrownBy recoveryMetrics.recordMetadataApplied(entity = null)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val fileSourceEntity = SourceEntity(
      path = Paths.get("/tmp/a"),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    val directorySourceEntity = SourceEntity(
      path = Paths.get("/tmp"),
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
    )

    val fileTargetEntity = TargetEntity(
      path = Paths.get("/tmp/a"),
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None
    )

    val directoryTargetEntity = TargetEntity(
      path = Paths.get("/tmp"),
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
      currentMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata)
    )

    val backupMetrics = new Metrics.BackupOperation.Default(meter = meter, namespace = "test")
    backupMetrics.recordEntityExamined(entity = fileSourceEntity)
    backupMetrics.recordEntitySkipped(entity = fileSourceEntity)
    backupMetrics.recordEntityCollected(entity = directorySourceEntity)
    backupMetrics.recordEntityChunkProcessed(step = "a", bytes = 1)
    backupMetrics.recordEntityChunkProcessed(step = "b", bytes = 2)
    backupMetrics.recordEntityChunkProcessed(step = "c", extra = "d", bytes = 2)
    backupMetrics.recordEntityProcessed(metadata = Left(fileSourceEntity.currentMetadata))

    meter.metric(name = "test_operations_backup_entities_handled") should be(4)
    meter.metric(name = "test_operations_backup_entity_handled_bytes") should be(4)
    meter.metric(name = "test_operations_backup_entity_chunks_processed") should be(3)
    meter.metric(name = "test_operations_backup_entity_chunk_processed_bytes") should be(3)

    val recoveryMetrics = new Metrics.RecoveryOperation.Default(meter = meter, namespace = "test")
    recoveryMetrics.recordEntityExamined(entity = fileTargetEntity)
    recoveryMetrics.recordEntityCollected(entity = directoryTargetEntity)
    recoveryMetrics.recordEntityChunkProcessed(step = "a", bytes = 1)
    recoveryMetrics.recordEntityChunkProcessed(step = "b", bytes = 2)
    recoveryMetrics.recordEntityChunkProcessed(step = "c", extra = "d", bytes = 2)
    recoveryMetrics.recordEntityProcessed(entity = fileTargetEntity)
    recoveryMetrics.recordMetadataApplied(entity = directoryTargetEntity)

    meter.metric(name = "test_operations_recovery_entities_handled") should be(4)
    meter.metric(name = "test_operations_recovery_entity_handled_bytes") should be(4)
    meter.metric(name = "test_operations_recovery_entity_chunks_processed") should be(3)
    meter.metric(name = "test_operations_recovery_entity_chunk_processed_bytes") should be(3)
  }
}
