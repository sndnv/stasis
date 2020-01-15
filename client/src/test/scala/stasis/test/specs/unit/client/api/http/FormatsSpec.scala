package stasis.test.specs.unit.client.api.http

import java.nio.file.{Path, Paths}

import play.api.libs.json._
import stasis.client.api.http.Formats._
import stasis.client.model.FilesystemMetadata
import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.shared.model.Generators

class FormatsSpec extends UnitSpec with ResourceHelpers {
  "Formats" should "convert paths to/from JSON" in {
    val path = "/ops/scheduling/test.file".asTestResource
    val json = s""""${path.toAbsolutePath.toString}""""

    pathFormat.writes(path).toString should be(json)
    pathFormat.reads(Json.parse(json)).asOpt should be(Some(path))
  }

  they should "convert file state to/from JSON" in {
    val entry = DatasetEntry.generateId()

    val fileStates = Map(
      FilesystemMetadata.FileState.New -> """{"file_state":"new"}""",
      FilesystemMetadata.FileState.Existing(entry) -> s"""{"file_state":"existing","entry":"$entry"}""",
      FilesystemMetadata.FileState.Updated -> """{"file_state":"updated"}"""
    )

    fileStates.foreach {
      case (state, json) =>
        fileStateFormat.writes(state).toString should be(json)
        fileStateFormat.reads(Json.parse(json)).asOpt should be(Some(state))
    }
  }

  they should "convert path maps to/from JSON" in {
    val path1 = Paths.get("test-path-01").toAbsolutePath
    val path2 = Paths.get("test-path-02").toAbsolutePath
    val path3 = Paths.get("test-path-03").toAbsolutePath

    val paths = Map[Path, Int](
      path1 -> 1,
      path2 -> 2,
      path3 -> 3,
    )

    val json = s"""{"$path1":1,"$path2":2,"$path3":3}"""

    pathMapFormat[Int].writes(paths).toString should be(json)
    pathMapFormat[Int].reads(Json.parse(json)).asOpt should be(Some(paths))
  }

  they should "convert schedule assignments to/from JSON" in {
    val schedule = Schedule.generateId()
    val backupDefinition = DatasetDefinition.generateId()

    val assignments = Map(
      OperationScheduleAssignment.Backup(
        schedule = schedule,
        definition = backupDefinition,
        files = Seq.empty
      ) -> s"""{"schedule":"$schedule","assignment_type":"backup","files":[],"definition":"$backupDefinition"}""",
      OperationScheduleAssignment.Expiration(
        schedule = schedule
      ) -> s"""{"schedule":"$schedule","assignment_type":"expiration"}""",
      OperationScheduleAssignment.Validation(
        schedule = schedule
      ) -> s"""{"schedule":"$schedule","assignment_type":"validation"}""",
      OperationScheduleAssignment.KeyRotation(
        schedule = schedule
      ) -> s"""{"schedule":"$schedule","assignment_type":"key-rotation"}""",
    )

    assignments.foreach {
      case (assignment, json) =>
        scheduleAssignmentFormat.writes(assignment).toString should be(json)
        scheduleAssignmentFormat.reads(Json.parse(json)).asOpt should be(Some(assignment))
    }
  }

  they should "convert active schedule retrieval results to/from JSON" in {
    import stasis.shared.api.Formats.scheduleFormat

    val schedule = Generators.generateSchedule

    val results = Map(
      Right(schedule) -> (Json.toJson(schedule).as[JsObject] ++ Json.obj("retrieval" -> "successful")).toString,
      Left(ScheduleRetrievalFailure("test failure")) -> """{"retrieval":"failed","message":"test failure"}"""
    )

    results.foreach {
      case (result, json) =>
        activeScheduleRetrievalResultFormat.writes(result).toString should be(json)
        activeScheduleRetrievalResultFormat.reads(Json.parse(json)).asOpt should be(Some(result))
    }
  }
}
