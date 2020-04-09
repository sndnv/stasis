package stasis.test.specs.unit.client.ops.scheduling

import java.nio.file.Paths
import java.util.UUID

import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.test.specs.unit.UnitSpec

import scala.util.{Failure, Success}

class OperationScheduleAssignmentSpec extends UnitSpec {
  "An OperationScheduleAssignment" should "support extracting UUIDs from a raw assignment string" in {
    val expectedUuid = UUID.randomUUID()

    OperationScheduleAssignment.extractUuid(uuid = expectedUuid.toString) should be(Success(expectedUuid))
  }

  it should "support extracting backup schedule assignments from a raw assignment string" in {
    val scheduleId = UUID.randomUUID()
    val definitionId = UUID.randomUUID()

    val validParameters = Map(
      s"$definitionId  " -> OperationScheduleAssignment.Backup(
        schedule = scheduleId,
        definition = definitionId,
        entities = Seq.empty
      ),
      s"  $definitionId file-01,file-02 , file-03   " -> OperationScheduleAssignment.Backup(
        schedule = scheduleId,
        definition = definitionId,
        entities = Seq(Paths.get("file-01"), Paths.get("file-02"), Paths.get("file-03"))
      )
    )

    val invalidParameters = Seq(
      Some(""),
      None
    )

    validParameters.foreach {
      case (parameters, expectedAssignment) =>
        val actualAssignment =
          OperationScheduleAssignment.Backup(schedule = scheduleId, parameters = Some(parameters), lineNumber = 0)
        withClue(s"Extracted backup schedule assignment parameters [$parameters] to [$actualAssignment]") {
          actualAssignment should be(Success(expectedAssignment))
        }
    }

    invalidParameters.foreach { parameters =>
      withClue(s"Attempting to extract backup schedule assignment from invalid parameters [$parameters]") {
        OperationScheduleAssignment.Backup(schedule = scheduleId, parameters = parameters, lineNumber = 0) match {
          case Success(result) =>
            fail(s"Unexpected result received: [$result]")

          case Failure(e) =>
            e.getMessage should startWith("Invalid backup schedule assignment parameters provided")
        }
      }
    }
  }

  it should "support extracting schedule assignments from a raw assignment string" in {
    val scheduleId = UUID.randomUUID()
    val definitionId = UUID.randomUUID()

    val validAssignments = Map(
      s"backup $scheduleId $definitionId" -> OperationScheduleAssignment.Backup(
        schedule = scheduleId,
        definition = definitionId,
        entities = Seq.empty
      ),
      s"backup $scheduleId $definitionId # comment" -> OperationScheduleAssignment.Backup(
        schedule = scheduleId,
        definition = definitionId,
        entities = Seq.empty
      ),
      s"backup $scheduleId $definitionId file-01,file-02 , file-03  // comment" -> OperationScheduleAssignment.Backup(
        schedule = scheduleId,
        definition = definitionId,
        entities = Seq(Paths.get("file-01"), Paths.get("file-02"), Paths.get("file-03"))
      ),
      s"expiration    $scheduleId # comment" -> OperationScheduleAssignment.Expiration(schedule = scheduleId),
      s"validation    $scheduleId // comment" -> OperationScheduleAssignment.Validation(schedule = scheduleId),
      s"key-rotation  $scheduleId" -> OperationScheduleAssignment.KeyRotation(schedule = scheduleId)
    )

    val invalidAssignments = Seq(
      s"backup $scheduleId",
      s"# comment",
      s"$scheduleId",
      s"other $scheduleId",
      ""
    )

    validAssignments.foreach {
      case (assignmentString, expectedAssignment) =>
        withClue(s"Parsing valid schedule assignment [$assignmentString]") {
          OperationScheduleAssignment(line = assignmentString, lineNumber = 0) should be(Success(expectedAssignment))
        }
    }

    invalidAssignments.foreach { assignmentString =>
      withClue(s"Parsing invalid schedule assignment [$assignmentString]") {
        OperationScheduleAssignment(line = assignmentString, lineNumber = 0) match {
          case Success(result) =>
            fail(s"Unexpected result received: [$result]")

          case Failure(e) =>
            e.getMessage should (
              startWith(
                "Invalid backup schedule assignment parameters provided"
              ) or startWith(
                "Invalid schedule assignment found"
              ) or startWith(
                "Invalid schedule assignment operation type provided"
              )
            )
        }
      }
    }
  }
}
