package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import stasis.client.ops.scheduling.{OperationScheduleAssignment, OperationScheduler}
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.client.mocks.MockOperationScheduler.Statistic
import stasis.test.specs.unit.shared.model.Generators

import scala.concurrent.Future

class MockOperationScheduler extends OperationScheduler {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.GetSchedules -> new AtomicInteger(0),
    Statistic.RefreshSchedules -> new AtomicInteger(0),
    Statistic.StopScheduler -> new AtomicInteger(0)
  )

  override def schedules: Future[Seq[OperationScheduler.ActiveSchedule]] = {

    stats(Statistic.GetSchedules).incrementAndGet()

    Future.successful(
      Seq(
        OperationScheduler.ActiveSchedule(
          assignment = OperationScheduleAssignment.Backup(
            schedule = Schedule.generateId(),
            definition = DatasetDefinition.generateId(),
            entities = Seq.empty
          ),
          schedule = Right(Generators.generateSchedule)
        ),
        OperationScheduler.ActiveSchedule(
          assignment = OperationScheduleAssignment.Expiration(schedule = Schedule.generateId()),
          schedule = Right(Generators.generateSchedule)
        ),
        OperationScheduler.ActiveSchedule(
          assignment = OperationScheduleAssignment.Validation(schedule = Schedule.generateId()),
          schedule = Right(Generators.generateSchedule)
        ),
        OperationScheduler.ActiveSchedule(
          assignment = OperationScheduleAssignment.KeyRotation(schedule = Schedule.generateId()),
          schedule = Right(Generators.generateSchedule)
        )
      )
    )
  }

  override def refresh(): Future[Done] = {
    stats(Statistic.RefreshSchedules).incrementAndGet()
    Future.successful(Done)
  }

  override def stop(): Future[Done] = {
    stats(Statistic.StopScheduler).incrementAndGet()
    Future.successful(Done)
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockOperationScheduler {
  def apply(): MockOperationScheduler = new MockOperationScheduler()

  sealed trait Statistic
  object Statistic {
    case object GetSchedules extends Statistic
    case object RefreshSchedules extends Statistic
    case object StopScheduler extends Statistic
  }
}
