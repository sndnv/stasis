package stasis.server.persistence.devices

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device
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
    backend
      .put(code.value, code)
      .map { result =>
        val expiresIn = Instant.now().until(code.expiresAt, ChronoUnit.MILLIS).millis
        val _ = org.apache.pekko.pattern.after(expiresIn)(delete(code.value))
        result
      }
  }

  override protected[devices] def delete(code: String): Future[Boolean] = metrics.recordDelete(store = name) {
    backend.delete(code)
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

  override protected[devices] def list(): Future[Seq[DeviceBootstrapCode]] = metrics.recordList(store = name) {
    backend.entries.map(_.values.map(_.copy(value = "*****")).toSeq)
  }

  override protected[devices] def find(forDevice: Device.Id): Future[Option[DeviceBootstrapCode]] =
    metrics.recordGet(store = name) {
      backend.entries.map(_.collectFirst { case (_, code) if code.device == forDevice => code })
    }
}
