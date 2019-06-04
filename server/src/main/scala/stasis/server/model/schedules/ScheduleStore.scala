package stasis.server.model.schedules

import scala.concurrent.Future

import akka.Done
import stasis.server.security.Resource
import stasis.shared.model.schedules.Schedule
import stasis.shared.security.Permission

trait ScheduleStore { store =>
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
  }

  object Manage {
    sealed trait Service extends Resource {
      def create(schedule: Schedule): Future[Done]
      def update(schedule: Schedule): Future[Done]
      def delete(schedule: Schedule.Id): Future[Boolean]
      override def requiredPermission: Permission = Permission.Manage.Service
    }
  }
}
