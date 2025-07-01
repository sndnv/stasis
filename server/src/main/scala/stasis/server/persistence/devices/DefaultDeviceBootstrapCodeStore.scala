package stasis.server.persistence.devices

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem

import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.Metrics
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.DeviceBootstrapCode

class DefaultDeviceBootstrapCodeStore(
  override val name: String,
  backend: KeyValueStore[String, DeviceBootstrapCode]
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends DeviceBootstrapCodeStore {
  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] = backend.init()

  override def drop(): Future[Done] = backend.drop()

  override protected[devices] def put(code: DeviceBootstrapCode): Future[Done] = metrics.recordPut(store = name) {
    for {
      existing <- backend.entries
      device = code.target.swap.toOption
      existing <- Future.successful(existing.filter(e => e._2.owner == code.owner && e._2.target.swap.toOption == device).keys)
      _ <- Future.sequence(existing.map(backend.delete))
      result <- backend.put(code.value, code)
    } yield {
      val expiresIn = Instant.now().until(code.expiresAt, ChronoUnit.MILLIS).millis
      val _ = org.apache.pekko.pattern.after(expiresIn)(delete(code.id))
      result
    }
  }

  override protected[devices] def delete(code: String): Future[Boolean] = metrics.recordDelete(store = name) {
    backend.delete(code)
  }

  override protected[devices] def delete(code: DeviceBootstrapCode.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    get(code).flatMap {
      case Some(existing) => delete(existing.value)
      case None           => Future.successful(false)
    }
  }

  override protected[devices] def consume(code: String): Future[Option[DeviceBootstrapCode]] =
    for {
      result <- get(code)
      _ <- delete(code)
    } yield {
      result
    }

  override protected[devices] def get(code: String): Future[Option[DeviceBootstrapCode]] = metrics.recordGet(store = name) {
    backend.get(code)
  }

  override protected[devices] def get(code: DeviceBootstrapCode.Id): Future[Option[DeviceBootstrapCode]] =
    metrics.recordGet(store = name) {
      backend.entries.map(_.collectFirst { case (_, c) if c.id == code => c })
    }

  override protected[devices] def list(): Future[Seq[DeviceBootstrapCode]] = metrics.recordList(store = name) {
    backend.entries.map(_.values.map(_.copy(value = "*****")).toSeq)
  }
}
