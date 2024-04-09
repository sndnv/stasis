package stasis.test.specs.unit.client.encryption.secrets

import org.apache.pekko.actor.ActorSystem
import stasis.client.encryption.secrets.{DeviceSecret, UserKeyStoreEncryptionSecret}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserKeyStoreEncryptionSecretSpec extends AsyncUnitSpec with SecretsConfig with EncodingHelpers {
  "A UserKeyStoreEncryptionSecret" should "encrypt device secrets" in {
    keyStoreEncryptionSecret
      .encryptDeviceSecret(
        secret = deviceSecret
      )
      .map { actualEncryptedSecret =>
        actualEncryptedSecret should be(encryptedDeviceSecret)
      }
  }

  it should "decrypt device secrets" in {
    keyStoreEncryptionSecret
      .decryptDeviceSecret(
        device = testDevice,
        encryptedSecret = encryptedDeviceSecret
      )
      .map { actualDeviceSecret =>
        actualDeviceSecret should be(deviceSecret)
      }
  }

  it should "not render its content via toString" in {
    keyStoreEncryptionSecret.toString should be(s"Secret(${keyStoreEncryptionSecret.getClass.getName})")
  }

  private implicit val system: ActorSystem = ActorSystem(name = "UserKeyStoreEncryptionSecretSpec")

  private val encryptionIv = "6mSjkDoXUNNK8TGbebRYWXnjfskeVHXhaMxBRKD+ITvMckUp0ZQtUeEttz9pA0vWQ4MKa8otGmyD\nJ7OCrdeY4g=="
  private val encryptionKey = "kROAgx70MxRKeDODRMyshRH0tswcd4jydKA60r+5knI="
  private val secret = "BOunLSLKxVbluhDSPZ/wWw=="

  private val keyStoreEncryptionSecret = UserKeyStoreEncryptionSecret(
    user = testUser,
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )

  private val deviceSecret = DeviceSecret(
    user = testUser,
    device = testDevice,
    secret = secret.decodeFromBase64
  )

  private val encryptedDeviceSecret = "lEc6SelbtpNPtzcm6AjkXQR51FNeWuM0xkWSXKARiFQ=".decodeFromBase64
}
