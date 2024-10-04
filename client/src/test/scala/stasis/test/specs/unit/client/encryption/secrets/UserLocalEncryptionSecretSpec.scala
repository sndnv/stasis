package stasis.test.specs.unit.client.encryption.secrets

import org.apache.pekko.actor.ActorSystem

import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.encryption.secrets.UserLocalEncryptionSecret
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserLocalEncryptionSecretSpec extends AsyncUnitSpec with SecretsConfig with EncodingHelpers {
  "A UserLocalEncryptionSecret" should "encrypt device secrets" in {
    localEncryptionSecret
      .encryptDeviceSecret(
        secret = deviceSecret
      )
      .map { actualEncryptedSecret =>
        actualEncryptedSecret should be(encryptedDeviceSecret)
      }
  }

  it should "decrypt device secrets" in {
    localEncryptionSecret
      .decryptDeviceSecret(
        device = testDevice,
        encryptedSecret = encryptedDeviceSecret
      )
      .map { actualDeviceSecret =>
        actualDeviceSecret should be(deviceSecret)
      }
  }

  it should "not render its content via toString" in {
    localEncryptionSecret.toString should be(s"Secret(${localEncryptionSecret.getClass.getName})")
  }

  private implicit val system: ActorSystem = ActorSystem(name = "UserEncryptionSecretSpec")

  private val encryptionIv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
  private val encryptionKey = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="
  private val secret = "BOunLSLKxVbluhDSPZ/wWw=="

  private val localEncryptionSecret = UserLocalEncryptionSecret(
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
}
