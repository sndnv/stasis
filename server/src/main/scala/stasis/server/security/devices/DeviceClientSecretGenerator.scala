package stasis.server.security.devices

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait DeviceClientSecretGenerator { generator =>
  def generate(): Future[String]
}

object DeviceClientSecretGenerator {
  final val MinSecretSize: Int = 16

  def apply(
    secretSize: Int
  )(implicit ec: ExecutionContext): DeviceClientSecretGenerator = {
    require(
      secretSize >= MinSecretSize,
      s"Expected device client secret size of at least [${MinSecretSize.toString}] but [${secretSize.toString}] provided"
    )

    new DeviceClientSecretGenerator {
      override def generate(): Future[String] =
        Future {
          val rnd: Random = ThreadLocalRandom.current()
          rnd.alphanumeric.take(secretSize).mkString
        }
    }
  }
}
