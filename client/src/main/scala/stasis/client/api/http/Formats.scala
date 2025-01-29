package stasis.client.api.http

import java.nio.file.Path
import java.nio.file.Paths

import play.api.libs.json.Format.GenericFormat

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.Specification
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityMetadata
import stasis.client.model.FilesystemMetadata
import stasis.client.ops.commands.ProcessedCommand
import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.client.ops.scheduling.OperationScheduler.ActiveSchedule
import stasis.client.ops.search.Search
import stasis.client.tracking.ServerTracker.ServerState
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.BackupState.PendingSourceEntity
import stasis.client.tracking.state.BackupState.ProcessedSourceEntity
import stasis.client.tracking.state.RecoveryState
import stasis.client.tracking.state.RecoveryState.PendingTargetEntity
import stasis.client.tracking.state.RecoveryState.ProcessedTargetEntity
import stasis.core.commands.proto.Command
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.schedules.Schedule

object Formats {
  import play.api.libs.json._

  import stasis.layers.api.Formats.optionFormat
  import stasis.layers.api.Formats.uuidMapFormat
  import stasis.shared.api.Formats.commandFormat
  import stasis.shared.api.Formats.scheduleFormat

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val pathFormat: Format[Path] = Format(
    fjs = _.validate[String].map(Paths.get(_)),
    tjs = path => Json.toJson(path.toAbsolutePath.toString)
  )

  implicit val entityStateFormat: Format[FilesystemMetadata.EntityState] = Format(
    fjs = _.validate[JsObject].flatMap { state =>
      (state \ "entity_state").validate[String].map {
        case "new" =>
          FilesystemMetadata.EntityState.New

        case "existing" =>
          FilesystemMetadata.EntityState.Existing(entry = (state \ "entry").as[DatasetEntry.Id])

        case "updated" =>
          FilesystemMetadata.EntityState.Updated
      }
    },
    tjs = {
      case FilesystemMetadata.EntityState.New =>
        Json.obj("entity_state" -> "new")

      case FilesystemMetadata.EntityState.Existing(entry) =>
        Json.obj("entity_state" -> "existing", "entry" -> entry)

      case FilesystemMetadata.EntityState.Updated =>
        Json.obj("entity_state" -> "updated")
    }
  )

  implicit def pathMapFormat[V](implicit format: Format[V]): Format[Map[Path, V]] =
    Format(
      fjs = _.validate[Map[String, V]].map(_.map { case (k, v) => Paths.get(k) -> v }),
      tjs = map => JsObject(map.map { case (k, v) => k.toAbsolutePath.toString -> format.writes(v) })
    )

  implicit val backupScheduleAssignment: Format[OperationScheduleAssignment.Backup] =
    Json.format[OperationScheduleAssignment.Backup]

  implicit val expirationScheduleAssignment: Format[OperationScheduleAssignment.Expiration] =
    Json.format[OperationScheduleAssignment.Expiration]

  implicit val validationScheduleAssignment: Format[OperationScheduleAssignment.Validation] =
    Json.format[OperationScheduleAssignment.Validation]

  implicit val keyRotationScheduleAssignment: Format[OperationScheduleAssignment.KeyRotation] =
    Json.format[OperationScheduleAssignment.KeyRotation]

  implicit val scheduleAssignmentFormat: Format[OperationScheduleAssignment] = Format(
    fjs = _.validate[JsObject].flatMap { assignment =>
      (assignment \ "assignment_type").validate[String].map {
        case "backup"       => assignment.as[OperationScheduleAssignment.Backup]
        case "expiration"   => assignment.as[OperationScheduleAssignment.Expiration]
        case "validation"   => assignment.as[OperationScheduleAssignment.Validation]
        case "key-rotation" => assignment.as[OperationScheduleAssignment.KeyRotation]
      }
    },
    tjs = {
      case backup: OperationScheduleAssignment.Backup =>
        backupScheduleAssignment.writes(backup).as[JsObject] ++ Json.obj(
          "assignment_type" -> Json.toJson("backup")
        )

      case expiration: OperationScheduleAssignment.Expiration =>
        expirationScheduleAssignment.writes(expiration).as[JsObject] ++ Json.obj(
          "assignment_type" -> Json.toJson("expiration")
        )

      case validation: OperationScheduleAssignment.Validation =>
        validationScheduleAssignment.writes(validation).as[JsObject] ++ Json.obj(
          "assignment_type" -> Json.toJson("validation")
        )

      case keyRotation: OperationScheduleAssignment.KeyRotation =>
        keyRotationScheduleAssignment.writes(keyRotation).as[JsObject] ++ Json.obj(
          "assignment_type" -> Json.toJson("key-rotation")
        )
    }
  )

  implicit val activeScheduleRetrievalResultFormat: Format[Either[ScheduleRetrievalFailure, Schedule]] = Format(
    fjs = _.validate[JsObject].flatMap { schedule =>
      (schedule \ "retrieval").validate[String].map {
        case "failed"     => Left(ScheduleRetrievalFailure(message = (schedule \ "message").as[String]))
        case "successful" => Right(schedule.as[Schedule])
      }
    },
    tjs = {
      case Left(failure) =>
        Json.obj(
          "retrieval" -> Json.toJson("failed"),
          "message" -> Json.toJson(failure.message)
        )

      case Right(schedule) =>
        Json.toJson(schedule).as[JsObject] ++ Json.obj(
          "retrieval" -> Json.toJson("successful")
        )
    }
  )

  implicit val fileEntityMetadataFormat: Format[EntityMetadata.File] = Json.format[EntityMetadata.File]
  implicit val directoryEntityMetadataFormat: Format[EntityMetadata.Directory] = Json.format[EntityMetadata.Directory]

  implicit val entityMetadataFormat: Format[EntityMetadata] = Format(
    fjs = _.validate[JsObject].flatMap { entity =>
      (entity \ "entity_type").validate[String].map {
        case "file"      => entity.as[EntityMetadata.File]
        case "directory" => entity.as[EntityMetadata.Directory]
      }
    },
    tjs = {
      case file: EntityMetadata.File =>
        Json.toJson(file)(fileEntityMetadataFormat).as[JsObject] + ("entity_type" -> Json.toJson("file"))

      case directory: EntityMetadata.Directory =>
        Json.toJson(directory)(directoryEntityMetadataFormat).as[JsObject] + ("entity_type" -> Json.toJson("directory"))
    }
  )

  implicit val filesystemMetadata: Format[FilesystemMetadata] = Json.format[FilesystemMetadata]
  implicit val datasetMetadataFormat: Format[DatasetMetadata] = Json.format[DatasetMetadata]

  implicit val activeScheduleFormat: Format[ActiveSchedule] = Json.format[ActiveSchedule]

  implicit val serverStateFormat: Format[ServerState] = Json.format[ServerState]

  implicit val datasetDefinitionResultFormat: Format[Search.DatasetDefinitionResult] =
    Json.format[Search.DatasetDefinitionResult]

  implicit val searchResultFormat: Format[Search.Result] =
    Json.format[Search.Result]

  implicit val ruleOperationFormat: Format[Rule.Operation] = Format(
    fjs = _.validate[String].map {
      case "include" => Rule.Operation.Include
      case "exclude" => Rule.Operation.Exclude
    },
    tjs = {
      case Rule.Operation.Include => Json.toJson("include")
      case Rule.Operation.Exclude => Json.toJson("exclude")
    }
  )

  implicit val ruleOriginalFormat: Format[Rule.Original] =
    Json.format[Rule.Original]

  implicit val ruleFormat: Format[Rule] =
    Json.format[Rule]

  implicit val specificationEntryExplanationFormat: Format[Specification.Entry.Explanation] =
    Json.format[Specification.Entry.Explanation]

  implicit val pendingSourceEntityFormat: Writes[PendingSourceEntity] =
    Json.writes[PendingSourceEntity]

  implicit val pendingTargetEntityFormat: Writes[PendingTargetEntity] =
    Json.writes[PendingTargetEntity]

  implicit val processedSourceEntityFormat: Writes[ProcessedSourceEntity] =
    Writes { entity =>
      Json.obj(
        "expected_parts" -> Json.toJson(entity.expectedParts),
        "processed_parts" -> Json.toJson(entity.processedParts)
      )
    }

  implicit val processedTargetEntityFormat: Writes[ProcessedTargetEntity] =
    Json.writes[ProcessedTargetEntity]

  implicit val backupStateFormat: Writes[BackupState] =
    Writes { backup =>
      Json.obj(
        "operation" -> Json.toJson(backup.operation),
        "type" -> Json.toJson("backup"),
        "definition" -> Json.toJson(backup.definition),
        "started" -> Json.toJson(backup.started),
        "entities" -> Json.obj(
          "discovered" -> Json.toJson(backup.entities.discovered),
          "unmatched" -> Json.toJson(backup.entities.unmatched),
          "examined" -> Json.toJson(backup.entities.examined),
          "skipped" -> Json.toJson(backup.entities.skipped),
          "collected" -> Json.toJson(backup.entities.collected.keySet),
          "pending" -> Json.toJson(backup.entities.pending.map(e => e._1.toAbsolutePath.toString -> e._2)),
          "processed" -> Json.toJson(backup.entities.processed.map(e => e._1.toAbsolutePath.toString -> e._2)),
          "failed" -> Json.toJson(backup.entities.failed.map(e => e._1.toAbsolutePath.toString -> e._2))
        ),
        "metadata_collected" -> Json.toJson(backup.metadataCollected),
        "metadata_pushed" -> Json.toJson(backup.metadataPushed),
        "failures" -> Json.toJson(backup.failures),
        "completed" -> Json.toJson(backup.completed)
      )
    }

  implicit val recoveryStateFormat: Writes[RecoveryState] =
    Writes { recovery =>
      Json.obj(
        "operation" -> Json.toJson(recovery.operation),
        "type" -> Json.toJson("recovery"),
        "started" -> Json.toJson(recovery.started),
        "entities" -> Json.obj(
          "examined" -> Json.toJson(recovery.entities.examined),
          "collected" -> Json.toJson(recovery.entities.collected.keySet),
          "pending" -> Json.toJson(recovery.entities.pending.map(e => e._1.toAbsolutePath.toString -> e._2)),
          "processed" -> Json.toJson(recovery.entities.processed.map(e => e._1.toAbsolutePath.toString -> e._2)),
          "metadata_applied" -> Json.toJson(recovery.entities.metadataApplied),
          "failed" -> Json.toJson(recovery.entities.failed.map(e => e._1.toAbsolutePath.toString -> e._2))
        ),
        "failures" -> Json.toJson(recovery.failures),
        "completed" -> Json.toJson(recovery.completed)
      )
    }

  implicit val processedCommandFormat: Format[ProcessedCommand] = Format(
    fjs = _.validate[JsObject].flatMap { processedCommand =>
      (processedCommand \ "is_processed").validate[Boolean].map { isProcessed =>
        ProcessedCommand(command = processedCommand.as[Command], isProcessed = isProcessed)
      }
    },
    tjs = { processedCommand =>
      commandFormat.writes(processedCommand.command).as[JsObject] ++ Json.obj(
        "is_processed" -> Json.toJson(processedCommand.isProcessed)
      )
    }
  )
}
