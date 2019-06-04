package stasis.test.specs.unit.server.model.mocks

import scala.concurrent.{ExecutionContext, Future}

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.devices.DeviceStore
import stasis.shared.model.devices.Device

class MockDeviceStore()(implicit system: ActorSystem, timeout: Timeout) extends DeviceStore {
  private val backend: MemoryBackend[Device.Id, Device] =
    MemoryBackend.untyped[Device.Id, Device](
      s"mock-device-store-${java.util.UUID.randomUUID()}"
    )

  override protected implicit def ec: ExecutionContext = system.dispatcher

  override protected def create(device: Device): Future[Done] =
    backend.put(device.id, device)

  override protected def update(device: Device): Future[Done] =
    backend.put(device.id, device)

  override protected def delete(device: Device.Id): Future[Boolean] =
    backend.delete(device)

  override protected def get(device: Device.Id): Future[Option[Device]] =
    backend.get(device)

  override protected def list(): Future[Map[Device.Id, Device]] =
    backend.entries
}
