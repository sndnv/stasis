package stasis.core.persistence.reservations

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.Metrics
import stasis.core.routing.Node
import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.TelemetryContext

class DefaultReservationStore(
  override val name: String,
  expiration: FiniteDuration,
  backend: KeyValueStore[CrateStorageReservation.Id, CrateStorageReservation]
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ReservationStore {
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.ReservationStore]

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] = backend.init()

  override def drop(): Future[Done] = backend.drop()

  override def put(reservation: CrateStorageReservation): Future[Done] =
    for {
      _ <- backend.put(reservation.id, reservation)
    } yield {
      metrics.recordReservation(reservation)
      val _ = org.apache.pekko.pattern.after(expiration)(delete(reservation.crate, reservation.target))
      Done
    }

  override def delete(crate: Crate.Id, node: Node.Id): Future[Boolean] =
    for {
      existing <- findFor(crate = crate, node = node)
      result <- existing match {
        case Some(reservation) => backend.delete(reservation.id)
        case None              => Future.successful(false)
      }
    } yield {
      result
    }

  override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
    backend.get(reservation)

  override def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean] =
    findFor(crate = crate, node = node).map(_.isDefined)

  override def reservations: Future[Seq[CrateStorageReservation]] =
    backend.entries.map(_.values.toSeq)

  private def findFor(crate: Crate.Id, node: Node.Id): Future[Option[CrateStorageReservation]] =
    backend.entries.map(_.collectFirst {
      case (_, reservation) if reservation.crate == crate && reservation.target == node =>
        reservation
    })
}

object DefaultReservationStore {
  def apply(
    name: String,
    expiration: FiniteDuration
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): DefaultReservationStore = {
    val backend = MemoryStore[CrateStorageReservation.Id, CrateStorageReservation](name = s"$name-memory")
    new DefaultReservationStore(name = name, expiration = expiration, backend = backend)
  }
}
