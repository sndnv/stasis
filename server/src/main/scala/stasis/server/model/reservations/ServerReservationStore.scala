package stasis.server.model.reservations

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.server.security.Resource
import stasis.shared.security.Permission

import scala.concurrent.Future

trait ServerReservationStore { store =>
  protected def list(): Future[Map[CrateStorageReservation.Id, CrateStorageReservation]]

  final def view(): ServerReservationStore.View.Service =
    new ServerReservationStore.View.Service {
      override def list(): Future[Map[CrateStorageReservation.Id, CrateStorageReservation]] =
        store.list()
    }
}

object ServerReservationStore {
  object View {
    sealed trait Service extends Resource {
      def list(): Future[Map[CrateStorageReservation.Id, CrateStorageReservation]]
      override def requiredPermission: Permission = Permission.View.Service
    }
  }

  def apply(store: ReservationStore): ServerReservationStore =
    new ServerReservationStore {
      override protected def list(): Future[Map[CrateStorageReservation.Id, CrateStorageReservation]] =
        store.reservations
    }
}
