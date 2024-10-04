package stasis.client.ops.scheduling

import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

import scala.util.matching.Regex
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import stasis.client.ops.exceptions.ScheduleAssignmentParsingFailure
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.schedules.Schedule

sealed trait OperationScheduleAssignment {
  def schedule: Schedule.Id
}

object OperationScheduleAssignment {
  final case class Backup(
    override val schedule: Schedule.Id,
    definition: DatasetDefinition.Id,
    entities: Seq[Path]
  ) extends OperationScheduleAssignment

  object Backup {
    def apply(schedule: Schedule.Id, parameters: Option[String], lineNumber: Int): Try[Backup] =
      parameters.fold(List.empty[String])(_.trim.split(" ").filter(_.nonEmpty).toList) match {
        case definitionId :: entities =>
          extractUuid(definitionId).map { definition =>
            Backup(
              schedule = schedule,
              definition = definition,
              entities = entities.mkString(" ").split(",").map(_.trim).filter(_.nonEmpty).map(Paths.get(_)).toSeq
            )
          }

        case Nil =>
          Failure(
            new ScheduleAssignmentParsingFailure(
              s"Invalid backup schedule assignment parameters provided on line [${lineNumber.toString}]: [${parameters.getOrElse("none")}]"
            )
          )
      }
  }

  final case class Expiration(
    override val schedule: Schedule.Id
  ) extends OperationScheduleAssignment

  final case class Validation(
    override val schedule: Schedule.Id
  ) extends OperationScheduleAssignment

  final case class KeyRotation(
    override val schedule: Schedule.Id
  ) extends OperationScheduleAssignment

  private val scheduleAssignment: Regex = """^([\w\-]+)\s+([0-9a-fA-F\-]{36})(?:\s+(.*?))?(?:\s+(?:#|//).*)?$""".r

  def extractUuid(uuid: String): Try[UUID] =
    Try(UUID.fromString(uuid.trim))

  def apply(line: String, lineNumber: Int): Try[OperationScheduleAssignment] =
    line match {
      case scheduleAssignment(operationType, scheduleId, operationParameters) =>
        for {
          schedule <- extractUuid(scheduleId)
          assignment <- operationType.trim.toLowerCase match {
            case "backup" =>
              OperationScheduleAssignment.Backup(
                schedule = schedule,
                parameters = Option(operationParameters),
                lineNumber = lineNumber
              ): Try[OperationScheduleAssignment]

            case "expiration" =>
              Success(OperationScheduleAssignment.Expiration(schedule = schedule)): Try[OperationScheduleAssignment]

            case "validation" =>
              Success(OperationScheduleAssignment.Validation(schedule = schedule)): Try[OperationScheduleAssignment]

            case "key-rotation" =>
              Success(OperationScheduleAssignment.KeyRotation(schedule = schedule)): Try[OperationScheduleAssignment]

            case other =>
              Failure(
                new ScheduleAssignmentParsingFailure(
                  s"Invalid schedule assignment operation type provided on line [${lineNumber.toString}]: [$other]"
                )
              ): Try[OperationScheduleAssignment]
          }
        } yield {
          assignment
        }

      case other =>
        Failure(
          new ScheduleAssignmentParsingFailure(
            s"Invalid schedule assignment found on line [${lineNumber.toString}]: [$other]"
          )
        )
    }
}
