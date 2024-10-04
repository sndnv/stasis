package stasis.server.security.devices

import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

import stasis.server.security.CurrentUser
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode

trait DeviceBootstrapCodeGenerator { generator =>
  def generate(currentUser: CurrentUser, device: Device.Id): Future[DeviceBootstrapCode]
}

object DeviceBootstrapCodeGenerator {
  final val MinCodeSize: Int = 8

  def apply(
    codeSize: Int,
    expiration: FiniteDuration
  )(implicit ec: ExecutionContext): DeviceBootstrapCodeGenerator = {
    require(
      codeSize >= MinCodeSize,
      s"Expected device bootstrap code size of at least [${MinCodeSize.toString}] but [${codeSize.toString}] provided"
    )

    new DeviceBootstrapCodeGenerator {
      override def generate(currentUser: CurrentUser, device: Device.Id): Future[DeviceBootstrapCode] =
        Future {
          val rnd: Random = ThreadLocalRandom.current()

          DeviceBootstrapCode(
            value = rnd.alphanumeric.take(codeSize).mkString,
            owner = currentUser.id,
            device = device,
            expiresAt = Instant.now().plusMillis(expiration.toMillis)
          )
        }
    }
  }
}
