package stasis.test.specs.unit.client.encryption.secrets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class DeviceMetadataSecretSpec extends AsyncUnitSpec with SecretsConfig with EncodingHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "DeviceMetadataSecretSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val encryptionIv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
  private val encryptionKey = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

  private val plaintextData = "some-plaintext-data"
  private val encryptedData = "coKEpIHZVHZtPcYuojvMPcOD8S6a2sH4Xg0gPX8UTKz75Ts="

  private val metadataSecret = DeviceMetadataSecret(
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )

  "A DeviceMetadataSecret" should "support encryption" in {
    Source
      .single(ByteString(plaintextData))
      .via(metadataSecret.encryption)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedData =>
        actualEncryptedData should be(encryptedData.decodeFromBase64)
      }
  }

  it should "support decryption" in {
    Source
      .single(encryptedData.decodeFromBase64)
      .via(metadataSecret.decryption)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualPlaintextData =>
        actualPlaintextData should be(ByteString(plaintextData))
      }
  }

  it should "not render its content via toString" in {
    metadataSecret.toString should be(s"Secret(${metadataSecret.getClass.getName})")
  }
}
