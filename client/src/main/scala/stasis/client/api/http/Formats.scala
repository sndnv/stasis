package stasis.client.api.http

import java.nio.file.{Path, Paths}

import stasis.client.model.{DatasetMetadata, FileMetadata, FilesystemMetadata}
import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.client.ops.scheduling.OperationScheduler.ActiveSchedule
import stasis.client.ops.search.Search
import stasis.client.tracking.TrackerView.ServerState
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.schedules.Schedule

object Formats {
  import play.api.libs.json._
  import stasis.core.api.Formats.{optionFormat, uuidMapFormat}
  import stasis.shared.api.Formats.scheduleFormat

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val pathFormat: Format[Path] = Format(
    fjs = _.validate[String].map(Paths.get(_)),
    tjs = path => Json.toJson(path.toAbsolutePath.toString)
  )

  implicit val fileStateFormat: Format[FilesystemMetadata.FileState] = Format(
    fjs = _.validate[JsObject].flatMap { state =>
      (state \ "file_state").validate[String].map {
        case "new" =>
          FilesystemMetadata.FileState.New

        case "existing" =>
          FilesystemMetadata.FileState.Existing(entry = (state \ "entry").as[DatasetEntry.Id])

        case "updated" =>
          FilesystemMetadata.FileState.Updated
      }
    },
    tjs = {
      case FilesystemMetadata.FileState.New =>
        Json.obj("file_state" -> "new")

      case FilesystemMetadata.FileState.Existing(entry) =>
        Json.obj("file_state" -> "existing", "entry" -> entry)

      case FilesystemMetadata.FileState.Updated =>
        Json.obj("file_state" -> "updated")
    }
  )

  implicit def pathMapFormat[V](implicit format: Format[V]): Format[Map[Path, V]] = Format(
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

  implicit val fileMetadataFormat: Format[FileMetadata] = Json.format[FileMetadata]
  implicit val filesystemMetadata: Format[FilesystemMetadata] = Json.format[FilesystemMetadata]
  implicit val datasetMetadataFormat: Format[DatasetMetadata] = Json.format[DatasetMetadata]

  implicit val activeScheduleFormat: Format[ActiveSchedule] = Json.format[ActiveSchedule]

  implicit val serverStateFormat: Format[ServerState] = Json.format[ServerState]

  implicit val datasetDefinitionResultFormat: Format[Search.DatasetDefinitionResult] =
    Json.format[Search.DatasetDefinitionResult]

  implicit val searchResultFormat: Format[Search.Result] =
    Json.format[Search.Result]
}
