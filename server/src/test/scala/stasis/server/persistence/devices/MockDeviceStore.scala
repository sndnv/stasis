package stasis.server.persistence.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device

class MockDeviceStore(
  underlying: KeyValueStore[Device.Id, Device]
)(implicit system: ActorSystem[Nothing])
    extends DeviceStore {
  override protected implicit val ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[devices] def put(device: Device): Future[Done] = underlying.put(device.id, device)

  override protected[devices] def delete(device: Device.Id): Future[Boolean] = underlying.delete(device)

  override protected[devices] def get(device: Device.Id): Future[Option[Device]] = underlying.get(device)

  override protected[devices] def list(): Future[Seq[Device]] = underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockDeviceStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): DeviceStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val underlying = MemoryStore[Device.Id, Device](s"mock-device-store-${java.util.UUID.randomUUID()}")

    new MockDeviceStore(underlying = underlying)
  }
}
