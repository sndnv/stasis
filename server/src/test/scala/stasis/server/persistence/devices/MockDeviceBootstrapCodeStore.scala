package stasis.server.persistence.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.DeviceBootstrapCode

class MockDeviceBootstrapCodeStore(
  underlying: KeyValueStore[String, DeviceBootstrapCode]
)(implicit system: ActorSystem[Nothing])
    extends DeviceBootstrapCodeStore {
  override protected implicit val ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[devices] def put(code: DeviceBootstrapCode): Future[Done] =
    underlying.put(code.value, code)

  override protected[devices] def delete(code: String): Future[Boolean] =
    underlying.delete(code)

  override protected[devices] def delete(code: DeviceBootstrapCode.Id): Future[Boolean] =
    get(code).flatMap {
      case Some(existing) => delete(existing.value)
      case None           => Future.successful(false)
    }

  override protected[devices] def consume(code: String): Future[Option[DeviceBootstrapCode]] =
    for {
      result <- underlying.get(code)
      _ <- underlying.delete(code)
    } yield {
      result
    }

  override protected[devices] def get(code: String): Future[Option[DeviceBootstrapCode]] =
    underlying.get(code)

  override protected[devices] def get(code: DeviceBootstrapCode.Id): Future[Option[DeviceBootstrapCode]] =
    underlying.entries.map(_.collectFirst { case (_, c) if c.id == code => c })

  override protected[devices] def list(): Future[Seq[DeviceBootstrapCode]] =
    underlying.entries.map(_.values.map(_.copy(value = "*****")).toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockDeviceBootstrapCodeStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DeviceBootstrapCodeStore = {
    val underlying = MemoryStore[String, DeviceBootstrapCode](
      name = s"mock-device-bootstrap-code-store-${java.util.UUID.randomUUID()}"
    )

    new MockDeviceBootstrapCodeStore(underlying)
  }
}
