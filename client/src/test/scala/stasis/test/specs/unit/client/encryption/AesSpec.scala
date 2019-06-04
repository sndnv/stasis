package stasis.test.specs.unit.client.encryption

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}
import stasis.client.model.DatasetMetadata
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{EncodingHelpers, Fixtures, ResourceHelpers}

import scala.concurrent.Future

class AesSpec extends AsyncUnitSpec with EncodingHelpers with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "AesSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val plaintextFile = "/encryption/plaintext-file".asTestResource
  private val encryptedFile = "/encryption/encrypted-file".asTestResource

  private val encryptionIv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
  private val encryptionKey = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

  private val fileSecret = DeviceFileSecret(
    file = plaintextFile,
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )

  private val datasetMetadata = DatasetMetadata(
    contentChanged = Seq(Fixtures.Metadata.FileOneMetadata),
    metadataChanged = Seq(Fixtures.Metadata.FileTwoMetadata)
  )

  private val encryptedDatasetMetadata =
    "C73jzIPdVWcrNdsnv2CON8fnkRkRkC3M5IUlmp609/6r" +
      "6aurkP7/UvrkMwSc3Cw0xvURhpfaMVFvv9ZErYeFmz" +
      "ichDns60Uxn2jIXYwq09A3m8YVQlQKpiZW9gaI+c24" +
      "EaESMOij+vIXr7j5kSB447xS23iU/UutONXR64A6xj" +
      "kbBDhi0D26OQhovDRNu5VOjVxepQlV3zeOllvMI0kL" +
      "/5l9m94etXYETkbWKPLFTRnOpMhTt5xj2zbU/373oX" +
      "1NA5iRu13U"

  private val metadataSecret = DeviceMetadataSecret(
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )

  private val aes = new Aes()

  "An AES encoder/decoder implementation" should "encrypt files" in {
    for {
      actualEncryptedContent <- FileIO
        .fromPath(plaintextFile)
        .via(aes.encrypt(fileSecret = fileSecret))
        .runFold(ByteString.empty)(_ concat _)
      expectedEncryptedContent <- FileIO
        .fromPath(encryptedFile)
        .runFold(ByteString.empty)(_ concat _)
    } yield {
      actualEncryptedContent should be(expectedEncryptedContent)
    }
  }

  it should "decrypt files" in {
    for {
      actualDecryptedContent <- FileIO
        .fromPath(encryptedFile)
        .via(aes.decrypt(fileSecret = fileSecret))
        .runFold(ByteString.empty)(_ concat _)
      expectedDecryptedContent <- FileIO
        .fromPath(plaintextFile)
        .runFold(ByteString.empty)(_ concat _)
    } yield {
      actualDecryptedContent should be(expectedDecryptedContent)
    }
  }

  it should "encrypt files metadata" in {
    Source
      .single(DatasetMetadata.toByteString(datasetMetadata))
      .via(aes.encrypt(metadataSecret = metadataSecret))
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedDatasetMetadata =>
        actualEncryptedDatasetMetadata should be(encryptedDatasetMetadata.decodeFromBase64)
      }
  }

  it should "decrypt files metadata" in {
    Source
      .single(encryptedDatasetMetadata.decodeFromBase64)
      .via(aes.decrypt(metadataSecret = metadataSecret))
      .runFold(ByteString.empty)(_ concat _)
      .flatMap(rawMetadata => Future.fromTry(DatasetMetadata.fromByteString(rawMetadata)))
      .map { expectedDatasetMetadata =>
        expectedDatasetMetadata should be(datasetMetadata)
      }
  }

  it should "generate AES keys" in {
    Aes.generateKey().size should be(16)
  }
}
