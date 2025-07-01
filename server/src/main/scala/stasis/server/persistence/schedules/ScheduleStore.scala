package stasis.server.persistence.schedules

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import io.github.sndnv.layers.persistence.Store
import stasis.server.security.Resource
import stasis.shared.model.schedules.Schedule
import stasis.shared.security.Permission

trait ScheduleStore extends Store { store =>
  protected implicit def ec: ExecutionContext

  protected[schedules] def put(schedule: Schedule): Future[Done]
  protected[schedules] def delete(schedule: Schedule.Id): Future[Boolean]
  protected[schedules] def get(schedule: Schedule.Id): Future[Option[Schedule]]
  protected[schedules] def list(): Future[Seq[Schedule]]

  final def view(): ScheduleStore.View.Service =
    new ScheduleStore.View.Service {
      override def get(schedule: Schedule.Id): Future[Option[Schedule]] =
        store.get(schedule)

      override def list(): Future[Seq[Schedule]] =
        store.list()
    }

  final def viewPublic(): ScheduleStore.View.Public =
    new ScheduleStore.View.Public {
      override def get(schedule: Schedule.Id): Future[Option[Schedule]] =
        store.get(schedule).flatMap {
          case Some(schedule) if schedule.isPublic =>
            Future.successful(Some(schedule))

          case Some(schedule) =>
            Future.failed(new IllegalArgumentException(s"Schedule [${schedule.id.toString}] is not public"))

          case None =>
            Future.successful(None)
        }

      override def list(): Future[Seq[Schedule]] =
        store.list().map(_.filter(_.isPublic))
    }

  final def manage(): ScheduleStore.Manage.Service =
    new ScheduleStore.Manage.Service {
      override def put(schedule: Schedule): Future[Done] =
        store.put(schedule)

      override def delete(schedule: Schedule.Id): Future[Boolean] =
        store.delete(schedule)
    }
}

object ScheduleStore {
  object View {
    sealed trait Service extends Resource {
      def get(schedule: Schedule.Id): Future[Option[Schedule]]
      def list(): Future[Seq[Schedule]]
      override def requiredPermission: Permission = Permission.View.Service
    }

    sealed trait Public extends Resource {
      def get(schedule: Schedule.Id): Future[Option[Schedule]]
      def list(): Future[Seq[Schedule]]
      override def requiredPermission: Permission = Permission.View.Public
    }
  }

  object Manage {
    sealed trait Service extends Resource {
      def put(schedule: Schedule): Future[Done]
      def delete(schedule: Schedule.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }
  }
}
