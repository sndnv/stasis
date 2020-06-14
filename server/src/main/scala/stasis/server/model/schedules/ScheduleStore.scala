package stasis.server.model.schedules

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.server.security.Resource
import stasis.shared.model.schedules.Schedule
import stasis.shared.security.Permission

import scala.concurrent.{ExecutionContext, Future}

trait ScheduleStore { store =>
  protected implicit def ec: ExecutionContext

  protected def create(schedule: Schedule): Future[Done]
  protected def update(schedule: Schedule): Future[Done]
  protected def delete(schedule: Schedule.Id): Future[Boolean]
  protected def get(schedule: Schedule.Id): Future[Option[Schedule]]
  protected def list(): Future[Map[Schedule.Id, Schedule]]

  final def view(): ScheduleStore.View.Service =
    new ScheduleStore.View.Service {
      override def get(schedule: Schedule.Id): Future[Option[Schedule]] =
        store.get(schedule)

      override def list(): Future[Map[Schedule.Id, Schedule]] =
        store.list()
    }

  final def viewPublic(): ScheduleStore.View.Public =
    new ScheduleStore.View.Public {
      override def get(schedule: Schedule.Id): Future[Option[Schedule]] =
        store.get(schedule).flatMap {
          case Some(schedule) if schedule.isPublic =>
            Future.successful(Some(schedule))

          case Some(schedule) =>
            Future.failed(new IllegalArgumentException(s"Schedule [${schedule.id}] is not public"))

          case None =>
            Future.successful(None)
        }

      override def list(): Future[Map[Schedule.Id, Schedule]] =
        store.list().map(_.filter(_._2.isPublic))
    }

  final def manage(): ScheduleStore.Manage.Service =
    new ScheduleStore.Manage.Service {
      override def create(schedule: Schedule): Future[Done] =
        store.create(schedule)

      override def update(schedule: Schedule): Future[Done] =
        store.update(schedule)

      override def delete(schedule: Schedule.Id): Future[Boolean] =
        store.delete(schedule)
    }
}

object ScheduleStore {
  object View {
    sealed trait Service extends Resource {
      def get(schedule: Schedule.Id): Future[Option[Schedule]]
      def list(): Future[Map[Schedule.Id, Schedule]]
      override def requiredPermission: Permission = Permission.View.Service
    }

    sealed trait Public extends Resource {
      def get(schedule: Schedule.Id): Future[Option[Schedule]]
      def list(): Future[Map[Schedule.Id, Schedule]]
      override def requiredPermission: Permission = Permission.View.Public
    }
  }

  object Manage {
    sealed trait Service extends Resource {
      def create(schedule: Schedule): Future[Done]
      def update(schedule: Schedule): Future[Done]
      def delete(schedule: Schedule.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }
  }

  def apply(
    backend: KeyValueBackend[Schedule.Id, Schedule]
  )(implicit ctx: ExecutionContext): ScheduleStore =
    new ScheduleStore {
      override implicit protected def ec: ExecutionContext = ctx
      override protected def create(schedule: Schedule): Future[Done] = backend.put(schedule.id, schedule)
      override protected def update(schedule: Schedule): Future[Done] = backend.put(schedule.id, schedule)
      override protected def delete(schedule: Schedule.Id): Future[Boolean] = backend.delete(schedule)
      override protected def get(schedule: Schedule.Id): Future[Option[Schedule]] = backend.get(schedule)
      override protected def list(): Future[Map[Schedule.Id, Schedule]] = backend.entries
    }
}
