package stasis.client.ops

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter

import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.client.model.TargetEntity
import stasis.layers.telemetry.metrics.MeterExtensions.ExtendedMeter
import stasis.layers.telemetry.metrics.MetricsProvider

object Metrics {
  def noop(): Set[MetricsProvider] = Set(
    BackupOperation.NoOp,
    RecoveryOperation.NoOp
  )

  def default(meter: Meter, namespace: String): Set[MetricsProvider] = Set(
    new BackupOperation.Default(meter, namespace),
    new RecoveryOperation.Default(meter, namespace)
  )

  trait RecoveryOperation extends MetricsProvider {
    def recordEntityExamined(entity: TargetEntity): Unit
    def recordEntityCollected(entity: TargetEntity): Unit
    def recordEntityChunkProcessed(step: String, bytes: Int): Unit
    def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit
    def recordEntityProcessed(entity: TargetEntity): Unit
    def recordMetadataApplied(entity: TargetEntity): Unit
  }

  object RecoveryOperation {
    object NoOp extends RecoveryOperation {
      override def recordEntityExamined(entity: TargetEntity): Unit = ()
      override def recordEntityCollected(entity: TargetEntity): Unit = ()
      override def recordEntityChunkProcessed(step: String, bytes: Int): Unit = ()
      override def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit = ()
      override def recordEntityProcessed(entity: TargetEntity): Unit = ()
      override def recordMetadataApplied(entity: TargetEntity): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends RecoveryOperation {
      private val subsystem: String = "operations_recovery"

      private val entitiesHandled = meter.counter(name = s"${namespace}_${subsystem}_entities_handled")
      private val entityHandledBytes = meter.counter(name = s"${namespace}_${subsystem}_entity_handled_bytes")
      private val entityChunksProcessed = meter.counter(name = s"${namespace}_${subsystem}_entity_chunks_processed")
      private val entityChunkProcessedBytes = meter.counter(name = s"${namespace}_${subsystem}_entity_chunk_processed_bytes")

      override def recordEntityExamined(entity: TargetEntity): Unit = {
        val (entityType, entityState, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "examined",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "examined",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )
      }

      override def recordEntityCollected(entity: TargetEntity): Unit = {
        val (entityType, entityState, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "collected",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "collected",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )
      }

      override def recordEntityChunkProcessed(step: String, bytes: Int): Unit = {
        entityChunksProcessed.inc(Labels.Step -> step)
        entityChunkProcessedBytes.add(value = bytes.toLong, Labels.Step -> step)
      }

      override def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit = {
        entityChunksProcessed.inc(Labels.Step -> step, Labels.Extra -> extra)
        entityChunkProcessedBytes.add(value = bytes.toLong, Labels.Step -> step, Labels.Extra -> extra)
      }

      override def recordEntityProcessed(entity: TargetEntity): Unit = {
        val (entityType, entityState, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "processed",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "processed",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )
      }

      override def recordMetadataApplied(entity: TargetEntity): Unit = {
        val (entityType, entityState, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "metadata_applied",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "metadata_applied",
          Labels.Type -> entityType,
          Labels.State -> entityState
        )
      }

      private def getEntityLabelsAndSize(from: TargetEntity): (String, String, Long) = {
        val state = if (from.currentMetadata.isDefined) "available" else "missing"

        from.currentMetadata.getOrElse(from.existingMetadata) match {
          case file: EntityMetadata.File   => ("file", state, file.size)
          case _: EntityMetadata.Directory => ("directory", state, 0L)
        }
      }
    }
  }

  trait BackupOperation extends MetricsProvider {
    def recordEntityExamined(entity: SourceEntity): Unit
    def recordEntitySkipped(entity: SourceEntity): Unit
    def recordEntityCollected(entity: SourceEntity): Unit
    def recordEntityChunkProcessed(step: String, bytes: Int): Unit
    def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit
    def recordEntityProcessed(metadata: Either[EntityMetadata, EntityMetadata]): Unit
  }

  object BackupOperation {
    object NoOp extends BackupOperation {
      override def recordEntityExamined(entity: SourceEntity): Unit = ()
      override def recordEntitySkipped(entity: SourceEntity): Unit = ()
      override def recordEntityCollected(entity: SourceEntity): Unit = ()
      override def recordEntityChunkProcessed(step: String, bytes: Int): Unit = ()
      override def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit = ()
      override def recordEntityProcessed(metadata: Either[EntityMetadata, EntityMetadata]): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends BackupOperation {
      private val subsystem: String = "operations_backup"

      private val entitiesHandled = meter.counter(name = s"${namespace}_${subsystem}_entities_handled")
      private val entityHandledBytes = meter.counter(name = s"${namespace}_${subsystem}_entity_handled_bytes")
      private val entityChunksProcessed = meter.counter(name = s"${namespace}_${subsystem}_entity_chunks_processed")
      private val entityChunkProcessedBytes = meter.counter(name = s"${namespace}_${subsystem}_entity_chunk_processed_bytes")

      override def recordEntityExamined(entity: SourceEntity): Unit = {
        val (entityType, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "examined",
          Labels.Type -> entityType
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "examined",
          Labels.Type -> entityType
        )
      }

      override def recordEntitySkipped(entity: SourceEntity): Unit = {
        val (entityType, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "skipped",
          Labels.Type -> entityType
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "skipped",
          Labels.Type -> entityType
        )
      }

      override def recordEntityCollected(entity: SourceEntity): Unit = {
        val (entityType, entitySize) = getEntityLabelsAndSize(from = entity)

        entitiesHandled.inc(
          Labels.Step -> "collected",
          Labels.Type -> entityType
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "collected",
          Labels.Type -> entityType
        )
      }

      override def recordEntityChunkProcessed(step: String, bytes: Int): Unit = {
        entityChunksProcessed.inc(Labels.Step -> step)
        entityChunkProcessedBytes.add(value = bytes.toLong, Labels.Step -> step)
      }

      override def recordEntityChunkProcessed(step: String, extra: String, bytes: Int): Unit = {
        entityChunksProcessed.inc(Labels.Step -> step, Labels.Extra -> extra)
        entityChunkProcessedBytes.add(value = bytes.toLong, Labels.Step -> step, Labels.Extra -> extra)
      }

      override def recordEntityProcessed(metadata: Either[EntityMetadata, EntityMetadata]): Unit = {
        val (entityType, entitySize) = getEntityLabelsAndSize(from = metadata.fold(identity, identity))

        entitiesHandled.inc(
          Labels.Step -> "processed",
          Labels.Type -> entityType
        )

        entityHandledBytes.add(
          value = entitySize,
          Labels.Step -> "processed",
          Labels.Type -> entityType
        )
      }

      private def getEntityLabelsAndSize(from: SourceEntity): (String, Long) =
        getEntityLabelsAndSize(from.currentMetadata)

      private def getEntityLabelsAndSize(from: EntityMetadata): (String, Long) =
        from match {
          case file: EntityMetadata.File   => ("file", file.size)
          case _: EntityMetadata.Directory => ("directory", 0L)
        }
    }
  }

  object Labels {
    val Operation: AttributeKey[String] = AttributeKey.stringKey("operation")
    val Step: AttributeKey[String] = AttributeKey.stringKey("step")
    val Extra: AttributeKey[String] = AttributeKey.stringKey("extra")
    val Type: AttributeKey[String] = AttributeKey.stringKey("type")
    val State: AttributeKey[String] = AttributeKey.stringKey("state")
  }
}
