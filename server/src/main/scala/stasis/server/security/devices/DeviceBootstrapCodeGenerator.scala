package stasis.server.security.devices

import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode

trait DeviceBootstrapCodeGenerator { generator =>
  def generate(currentUser: CurrentUser, device: Device.Id): Future[DeviceBootstrapCode]
  def generate(currentUser: CurrentUser, request: CreateDeviceOwn): Future[DeviceBootstrapCode]
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
          DeviceBootstrapCode(
            value = generateValue(),
            owner = currentUser.id,
            device = device,
            expiresAt = Instant.now().plusMillis(expiration.toMillis)
          )
        }

      override def generate(currentUser: CurrentUser, request: CreateDeviceOwn): Future[DeviceBootstrapCode] =
        Future {
          DeviceBootstrapCode(
            value = generateValue(),
            owner = currentUser.id,
            request = request,
            expiresAt = Instant.now().plusMillis(expiration.toMillis)
          )
        }

      private def generateValue(): String = {
        val rnd: Random = ThreadLocalRandom.current()
        rnd.alphanumeric.take(codeSize).mkString
      }
    }
  }
}
