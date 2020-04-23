package stasis.test.specs.unit.client.api.http

import java.nio.file.{Path, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit

import play.api.libs.json._
import stasis.client.api.http.Formats._
import stasis.client.collection.rules.Rule
import stasis.client.model.{EntityMetadata, FilesystemMetadata}
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

  they should "convert entity state to/from JSON" in {
    val entry = DatasetEntry.generateId()

    val entityStates = Map(
      FilesystemMetadata.EntityState.New -> """{"entity_state":"new"}""",
      FilesystemMetadata.EntityState.Existing(entry) -> s"""{"entity_state":"existing","entry":"$entry"}""",
      FilesystemMetadata.EntityState.Updated -> """{"entity_state":"updated"}"""
    )

    entityStates.foreach {
      case (state, json) =>
        entityStateFormat.writes(state).toString should be(json)
        entityStateFormat.reads(Json.parse(json)).asOpt should be(Some(state))
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
        entities = Seq.empty
      ) -> s"""{"schedule":"$schedule","entities":[],"assignment_type":"backup","definition":"$backupDefinition"}""",
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

  they should "convert entity metadata to/from JSON" in {
    val fileMetadata = EntityMetadata.File(
      path = Paths.get("/tmp/file/one"),
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = 1,
      crates = Map(
        Paths.get("/tmp/file/one_0") -> java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
      )
    )

    val directoryMetadata = EntityMetadata.Directory(
      path = Paths.get("/tmp/file/one"),
      link = None,
      isHidden = false,
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx"
    )

    val baseFileMetadataJson = fileEntityMetadataFormat
      .writes(fileMetadata)
      .as[JsObject] ++ Json.obj("entity_type" -> "file")

    val baseDirectoryMetadataJson = directoryEntityMetadataFormat
      .writes(directoryMetadata)
      .as[JsObject] ++ Json.obj("entity_type" -> "directory")

    val metadata = Map[EntityMetadata, String](
      fileMetadata -> baseFileMetadataJson.toString,
      directoryMetadata -> baseDirectoryMetadataJson.toString
    )

    metadata.foreach {
      case (entity, json) =>
        entityMetadataFormat.writes(entity).toString should be(json)
        entityMetadataFormat.reads(Json.parse(json)).asOpt should be(Some(entity))
    }
  }

  they should "convert rule operations to/from JSON" in {
    val operations = Map(
      Rule.Operation.Include -> """"include"""",
      Rule.Operation.Exclude -> """"exclude""""
    )

    operations.foreach {
      case (operation, json) =>
        ruleOperationFormat.writes(operation).toString should be(json)
        ruleOperationFormat.reads(Json.parse(json)).asOpt should be(Some(operation))
    }
  }
}
