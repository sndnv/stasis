package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.client.model.TargetEntity
import stasis.client.ops.Metrics

object MockOpsMetrics {
  class BackupOperation extends Metrics.BackupOperation {
    private val entityExaminedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entitySkippedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entityCollectedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entityChunkProcessedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entityProcessedRecorded: AtomicInteger = new AtomicInteger(0)

    def entityExamined: Int = entityExaminedRecorded.get()
    def entitySkipped: Int = entitySkippedRecorded.get()
    def entityCollected: Int = entityCollectedRecorded.get()
    def entityChunkProcessed: Int = entityChunkProcessedRecorded.get()
    def entityProcessed: Int = entityProcessedRecorded.get()

    override def recordEntityExamined(entity: SourceEntity): Unit = {
      val _ = entityExaminedRecorded.incrementAndGet()
    }

    override def recordEntitySkipped(entity: SourceEntity): Unit = {
      val _ = entitySkippedRecorded.incrementAndGet()
    }

    override def recordEntityCollected(entity: SourceEntity): Unit = {
      val _ = entityCollectedRecorded.incrementAndGet()
    }

    override def recordEntityChunkProcessed(step: String, bytes: Int): Unit = {
      val _ = entityChunkProcessedRecorded.incrementAndGet()
    }

    override def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit = {
      val _ = entityChunkProcessedRecorded.incrementAndGet()
    }

    override def recordEntityProcessed(metadata: Either[EntityMetadata, EntityMetadata]): Unit = {
      val _ = entityProcessedRecorded.incrementAndGet()
    }

  }

  object BackupOperation {
    def apply(): BackupOperation = new BackupOperation()
  }

  class RecoveryOperation extends Metrics.RecoveryOperation {
    private val entityExaminedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entityCollectedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entityChunkProcessedRecorded: AtomicInteger = new AtomicInteger(0)
    private val entityProcessedRecorded: AtomicInteger = new AtomicInteger(0)
    private val metadataAppliedRecorded: AtomicInteger = new AtomicInteger(0)

    def entityExamined: Int = entityExaminedRecorded.get()
    def entityCollected: Int = entityCollectedRecorded.get()
    def entityChunkProcessed: Int = entityChunkProcessedRecorded.get()
    def entityProcessed: Int = entityProcessedRecorded.get()
    def metadataApplied: Int = metadataAppliedRecorded.get()

    override def recordEntityExamined(entity: TargetEntity): Unit = {
      val _ = entityExaminedRecorded.incrementAndGet()
    }

    override def recordEntityCollected(entity: TargetEntity): Unit = {
      val _ = entityCollectedRecorded.incrementAndGet()
    }

    override def recordEntityChunkProcessed(step: String, bytes: Int): Unit = {
      val _ = entityChunkProcessedRecorded.incrementAndGet()
    }

    override def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit = {
      val _ = entityChunkProcessedRecorded.incrementAndGet()
    }

    override def recordEntityProcessed(entity: TargetEntity): Unit = {
      val _ = entityProcessedRecorded.incrementAndGet()
    }

    override def recordMetadataApplied(entity: TargetEntity): Unit = {
      val _ = metadataAppliedRecorded.incrementAndGet()
    }
  }

  object RecoveryOperation {
    def apply(): RecoveryOperation = new RecoveryOperation()
  }
}
