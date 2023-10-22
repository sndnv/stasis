package stasis.test.specs.unit.client.encryption.secrets

import java.nio.file.Paths
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class DeviceFileSecretSpec extends AsyncUnitSpec with SecretsConfig with EncodingHelpers {
  "A DeviceFileSecret" should "support encryption" in {
    Source
      .single(ByteString(plaintextData))
      .via(fileSecret.encryption)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedData =>
        actualEncryptedData should be(encryptedData.decodeFromBase64)
      }
  }

  it should "support decryption" in {
    Source
      .single(encryptedData.decodeFromBase64)
      .via(fileSecret.decryption)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualPlaintextData =>
        actualPlaintextData should be(ByteString(plaintextData))
      }
  }

  it should "not render its content via toString" in {
    fileSecret.toString should be(s"Secret(${fileSecret.getClass.getName})")
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DeviceFileSecretSpec")

  private val encryptionIv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
  private val encryptionKey = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

  private val plaintextData = "some-plaintext-data"
  private val encryptedData = "coKEpIHZVHZtPcYuojvMPcOD8S6a2sH4Xg0gPX8UTKz75Ts="

  private val fileSecret = DeviceFileSecret(
    file = Paths.get("/tmp/some/file"),
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )
}
