package stasis.client.ops.scheduling

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.OperationScheduler.ActiveSchedule
import stasis.shared.model.schedules.Schedule

trait OperationScheduler {
  def schedules: Future[Seq[ActiveSchedule]]
  def refresh(): Future[Done]
  def stop(): Future[Done]
}

object OperationScheduler {
  final case class ActiveSchedule(
    assignment: OperationScheduleAssignment,
    schedule: Either[ScheduleRetrievalFailure, Schedule]
  )
}
