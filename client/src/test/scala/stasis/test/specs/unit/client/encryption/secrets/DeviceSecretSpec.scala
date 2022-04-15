package stasis.test.specs.unit.client.encryption.secrets

import java.nio.file.Paths
import akka.actor.ActorSystem
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret, DeviceSecret}
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class DeviceSecretSpec extends AsyncUnitSpec with SecretsConfig with EncodingHelpers {
  "A DeviceSecret" should "support encryption" in {
    val encryptionStage = Aes.encryption(
      key = encryptionKey.decodeFromBase64,
      iv = encryptionIv.decodeFromBase64
    )

    deviceSecret
      .encrypted(encryptionStage = encryptionStage)
      .map { actualEncryptedSecret =>
        actualEncryptedSecret should be(encryptedDeviceSecret)
      }
  }

  it should "support decryption" in {
    val decryptionStage = Aes.decryption(
      key = encryptionKey.decodeFromBase64,
      iv = encryptionIv.decodeFromBase64
    )

    DeviceSecret
      .decrypted(
        user = testUser,
        device = testDevice,
        encryptedSecret = encryptedDeviceSecret,
        decryptionStage = decryptionStage
      )
      .map { actualDeviceSecret =>
        actualDeviceSecret should be(deviceSecret)
      }
  }

  it should "support generating file secrets" in {
    val file = Paths.get("/tmp/some/file")

    val iv = "f09I6Ac7NhtF8xIQ7wIU9A=="
    val key = "rmoFSdgN+pmm4OSIZf+IHw=="

    deviceSecret.toFileSecret(forFile = file) should be(
      DeviceFileSecret(
        file = file,
        iv = iv.decodeFromBase64,
        key = key.decodeFromBase64
      )
    )
  }

  it should "support generating metadata secrets" in {
    val metadata: Crate.Id = java.util.UUID.fromString("2b94caba-7c28-4322-9d72-fc8e72f884d5")

    val iv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
    val key = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

    deviceSecret.toMetadataSecret(metadataCrate = metadata) should be(
      DeviceMetadataSecret(
        iv = iv.decodeFromBase64,
        key = key.decodeFromBase64
      )
    )
  }

  it should "not render its content via toString" in {
    deviceSecret.toString should be(s"Secret(${deviceSecret.getClass.getName})")
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DeviceSecretSpec")

  private val encryptionIv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
  private val encryptionKey = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="
  private val secret = "BOunLSLKxVbluhDSPZ/wWw=="

  private val deviceSecret = DeviceSecret(
    user = testUser,
    device = testDevice,
    secret = secret.decodeFromBase64
  )

  private val encryptedDeviceSecret = "z+pus9Glc/HssFVWozd+T2iuw4OOM4aeqcdDY+gvG8M=".decodeFromBase64
}
