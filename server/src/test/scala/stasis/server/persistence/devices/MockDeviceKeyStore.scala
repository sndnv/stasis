package stasis.server.persistence.devices

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey

class MockDeviceKeyStore(
  underlying: KeyValueStore[Device.Id, DeviceKey]
)(implicit system: ActorSystem[Nothing])
    extends DeviceKeyStore {
  override protected implicit val ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[devices] def put(key: DeviceKey): Future[Done] = underlying.put(key.device, key)

  override protected[devices] def delete(forDevice: Device.Id): Future[Boolean] = underlying.delete(forDevice)

  override protected[devices] def exists(forDevice: Device.Id): Future[Boolean] = underlying.contains(forDevice)

  override protected[devices] def get(forDevice: Device.Id): Future[Option[DeviceKey]] = underlying.get(forDevice)

  override protected[devices] def list(): Future[Seq[DeviceKey]] =
    underlying.entries.map(_.values.map(_.copy(value = ByteString.empty)).toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockDeviceKeyStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceKeyStore = MockDeviceKeyStore(withKeys = Seq.empty)

  def apply(withKeys: Seq[DeviceKey])(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceKeyStore = {
    import system.executionContext

    val underlying = MemoryStore[Device.Id, DeviceKey](
      s"mock-device-key-store-${java.util.UUID.randomUUID()}"
    )

    val _ = Await.result(Future.sequence(withKeys.map(key => underlying.put(key.device, key))), atMost = 1.second)

    new MockDeviceKeyStore(underlying)
  }
}
