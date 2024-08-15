package stasis.test.specs.unit.client.api.http

import java.nio.file.{Path, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit
import play.api.libs.json._
import stasis.client.api.http.Formats._
import stasis.client.collection.rules.Rule
import stasis.client.model.{EntityMetadata, FilesystemMetadata, SourceEntity, TargetEntity}
import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.client.tracking.state.{BackupState, RecoveryState}
import stasis.client.tracking.state.BackupState.{PendingSourceEntity, ProcessedSourceEntity}
import stasis.client.tracking.state.RecoveryState.{PendingTargetEntity, ProcessedTargetEntity}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.schedules.Schedule
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}
import stasis.test.specs.unit.shared.model.Generators

class FormatsSpec extends UnitSpec with ResourceHelpers {
  "Formats" should "convert paths to/from JSON" in withRetry {
    val path = "/ops/scheduling/test.file".asTestResource
    val json = s""""${path.toAbsolutePath.toString}""""

    pathFormat.writes(path).toString should be(json)
    pathFormat.reads(Json.parse(json)).asOpt should be(Some(path))
  }

  they should "convert entity state to/from JSON" in withRetry {
    val entry = DatasetEntry.generateId()

    val entityStates = Map(
      FilesystemMetadata.EntityState.New -> """{"entity_state":"new"}""",
      FilesystemMetadata.EntityState.Existing(entry) -> s"""{"entity_state":"existing","entry":"$entry"}""",
      FilesystemMetadata.EntityState.Updated -> """{"entity_state":"updated"}"""
    )

    entityStates.foreach { case (state, json) =>
      entityStateFormat.writes(state).toString should be(json)
      entityStateFormat.reads(Json.parse(json)).asOpt should be(Some(state))
    }

    succeed
  }

  they should "convert path maps to/from JSON" in withRetry {
    val path1 = Paths.get("test-path-01").toAbsolutePath
    val path2 = Paths.get("test-path-02").toAbsolutePath
    val path3 = Paths.get("test-path-03").toAbsolutePath

    val paths = Map[Path, Int](
      path1 -> 1,
      path2 -> 2,
      path3 -> 3
    )

    val json = s"""{"$path1":1,"$path2":2,"$path3":3}"""

    pathMapFormat[Int].writes(paths).toString should be(json)
    pathMapFormat[Int].reads(Json.parse(json)).asOpt should be(Some(paths))
  }

  they should "convert schedule assignments to/from JSON" in withRetry {
    val schedule = Schedule.generateId()
    val backupDefinition = DatasetDefinition.generateId()

    val assignments = Map(
      OperationScheduleAssignment.Backup(
        schedule = schedule,
        definition = backupDefinition,
        entities = Seq.empty
      ) -> s"""{"schedule":"$schedule","definition":"$backupDefinition","entities":[],"assignment_type":"backup"}""",
      OperationScheduleAssignment.Expiration(
        schedule = schedule
      ) -> s"""{"schedule":"$schedule","assignment_type":"expiration"}""",
      OperationScheduleAssignment.Validation(
        schedule = schedule
      ) -> s"""{"schedule":"$schedule","assignment_type":"validation"}""",
      OperationScheduleAssignment.KeyRotation(
        schedule = schedule
      ) -> s"""{"schedule":"$schedule","assignment_type":"key-rotation"}"""
    )

    assignments.foreach { case (assignment, json) =>
      scheduleAssignmentFormat.writes(assignment).toString should be(json)
      scheduleAssignmentFormat.reads(Json.parse(json)).asOpt should be(Some(assignment))
    }

    succeed
  }

  they should "convert active schedule retrieval results to/from JSON" in withRetry {
    import stasis.shared.api.Formats.scheduleFormat

    val schedule = Generators.generateSchedule

    val results = Map(
      Right(schedule) -> (Json.toJson(schedule).as[JsObject] ++ Json.obj("retrieval" -> "successful")).toString,
      Left(ScheduleRetrievalFailure("test failure")) -> """{"retrieval":"failed","message":"test failure"}"""
    )

    results.foreach { case (result, json) =>
      activeScheduleRetrievalResultFormat.writes(result).toString should be(json)
      activeScheduleRetrievalResultFormat.reads(Json.parse(json)).asOpt should be(Some(result))
    }

    succeed
  }

  they should "convert entity metadata to/from JSON" in withRetry {
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
      ),
      compression = "none"
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

    metadata.foreach { case (entity, json) =>
      entityMetadataFormat.writes(entity).toString should be(json)
      entityMetadataFormat.reads(Json.parse(json)).asOpt should be(Some(entity))
    }

    succeed
  }

  they should "convert rule operations to/from JSON" in withRetry {
    val operations = Map(
      Rule.Operation.Include -> """"include"""",
      Rule.Operation.Exclude -> """"exclude""""
    )

    operations.foreach { case (operation, json) =>
      ruleOperationFormat.writes(operation).toString should be(json)
      ruleOperationFormat.reads(Json.parse(json)).asOpt should be(Some(operation))
    }

    succeed
  }

  they should "convert processed source entities to/from JSON" in withRetry {
    val entity = ProcessedSourceEntity(
      expectedParts = 1,
      processedParts = 2,
      metadata = Left(Fixtures.Metadata.FileOneMetadata)
    )

    val json = """{"expected_parts":1,"processed_parts":2}"""

    processedSourceEntityFormat.writes(entity).toString should be(json)
  }

  they should "convert backup state to JSON" in withRetry {
    val entity1 = Fixtures.Metadata.FileOneMetadata.path
    val entity2 = Fixtures.Metadata.FileTwoMetadata.path
    val entity3 = Fixtures.Metadata.FileThreeMetadata.path

    val sourceEntity = SourceEntity(
      path = entity1,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    val now = Instant.now()

    val backup = BackupState(
      operation = Operation.generateId(),
      started = now,
      definition = Operation.generateId(),
      entities = BackupState.Entities(
        discovered = Set(entity1),
        unmatched = Seq("a", "b", "c"),
        examined = Set(entity2),
        skipped = Set.empty,
        collected = Map(entity1 -> sourceEntity),
        pending = Map(entity2 -> PendingSourceEntity(expectedParts = 1, processedParts = 2)),
        processed = Map(
          entity1 -> ProcessedSourceEntity(
            expectedParts = 1,
            processedParts = 1,
            metadata = Left(Fixtures.Metadata.FileOneMetadata)
          ),
          entity2 -> ProcessedSourceEntity(
            expectedParts = 0,
            processedParts = 0,
            metadata = Right(Fixtures.Metadata.FileTwoMetadata)
          )
        ),
        failed = Map(entity3 -> "x")
      ),
      metadataCollected = Some(now),
      metadataPushed = Some(now),
      failures = Seq("y", "z"),
      completed = Some(now)
    )

    val json =
      s"""
         |{
         |"operation":"${backup.operation.toString}",
         |"type":"backup",
         |"definition":"${backup.definition.toString}",
         |"started":"${now.toString}",
         |"entities":{
         |"discovered":["/tmp/file/one"],
         |"unmatched":["a","b","c"],
         |"examined":["/tmp/file/two"],
         |"skipped":[],
         |"collected":["/tmp/file/one"],
         |"pending":{"/tmp/file/two":{"expected_parts":1,"processed_parts":2}},
         |"processed":{"/tmp/file/one":{"expected_parts":1,"processed_parts":1},"/tmp/file/two":{"expected_parts":0,"processed_parts":0}},
         |"failed":{"/tmp/file/four":"x"}
         |},
         |"metadata_collected":"${now.toString}",
         |"metadata_pushed":"${now.toString}",
         |"failures":["y","z"],
         |"completed":"${now.toString}"
         |}""".stripMargin.replaceAll("\n", "").trim

    backupStateFormat.writes(backup).toString should be(json)
  }

  they should "convert recovery state to JSON" in withRetry {
    val entity1 = Fixtures.Metadata.FileOneMetadata.path
    val entity2 = Fixtures.Metadata.FileTwoMetadata.path
    val entity3 = Fixtures.Metadata.FileThreeMetadata.path

    val targetEntity = TargetEntity(
      path = entity1,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None,
      destination = TargetEntity.Destination.Default
    )

    val now = Instant.now()

    val recovery = RecoveryState(
      operation = Operation.generateId(),
      started = now,
      entities = RecoveryState.Entities(
        examined = Set(entity1, entity2, entity3),
        collected = Map(entity1 -> targetEntity),
        pending = Map(entity3 -> PendingTargetEntity(expectedParts = 3, processedParts = 1)),
        processed = Map(entity1 -> ProcessedTargetEntity(expectedParts = 1, processedParts = 1)),
        metadataApplied = Set(entity1),
        failed = Map(entity3 -> "x")
      ),
      failures = Seq("y", "z"),
      completed = Some(now)
    )

    val json =
      s"""
         |{
         |"operation":"${recovery.operation.toString}",
         |"type":"recovery",
         |"started":"${now.toString}",
         |"entities":{
         |"examined":["/tmp/file/one","/tmp/file/two","/tmp/file/four"],
         |"collected":["/tmp/file/one"],
         |"pending":{"/tmp/file/four":{"expected_parts":3,"processed_parts":1}},
         |"processed":{"/tmp/file/one":{"expected_parts":1,"processed_parts":1}},
         |"metadata_applied":["/tmp/file/one"],
         |"failed":{"/tmp/file/four":"x"}
         |},
         |"failures":["y","z"],
         |"completed":"${now.toString}"
         |}""".stripMargin.replaceAll("\n", "").trim

    recoveryStateFormat.writes(recovery).toString should be(json)
  }
}
