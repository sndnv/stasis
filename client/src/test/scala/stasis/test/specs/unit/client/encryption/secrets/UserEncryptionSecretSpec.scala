package stasis.test.specs.unit.client.encryption.secrets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.encryption.secrets.{DeviceSecret, UserEncryptionSecret}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserEncryptionSecretSpec extends AsyncUnitSpec with SecretsConfig with EncodingHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "UserEncryptionSecretSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val encryptionIv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
  private val encryptionKey = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="
  private val secret = "BOunLSLKxVbluhDSPZ/wWw=="

  private val encryptionSecret = UserEncryptionSecret(
    user = testUser,
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )

  private val deviceSecret = DeviceSecret(
    user = testUser,
    device = testDevice,
    secret = secret.decodeFromBase64
  )

  private val encryptedDeviceSecret = "z+pus9Glc/HssFVWozd+T2iuw4OOM4aeqcdDY+gvG8M=".decodeFromBase64

  "A UserEncryptionSecret" should "encrypt device secrets" in {
    encryptionSecret
      .encryptDeviceSecret(
        secret = deviceSecret
      )
      .map { actualEncryptedSecret =>
        actualEncryptedSecret should be(encryptedDeviceSecret)
      }
  }

  it should "decrypt device secrets" in {

    encryptionSecret
      .decryptDeviceSecret(
        device = testDevice,
        encryptedSecret = encryptedDeviceSecret
      )
      .map { actualDeviceSecret =>
        actualDeviceSecret should be(deviceSecret)
      }
  }

  it should "not render its content via toString" in {
    encryptionSecret.toString should be(s"Secret(${encryptionSecret.getClass.getName})")
  }
}
