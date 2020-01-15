package stasis.test.specs.unit.client.encryption

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
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
    contentChanged = Map(Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata),
    metadataChanged = Map(Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata),
    filesystem = FilesystemMetadata(
      files = Map(
        Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
        Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated
      )
    )
  )

  private val encryptedDatasetMetadata =
    "C4zjzIPdVWcrNdsnv2CON8flwDucF8kCFUi7NA3Q6G40" +
      "WW8FIvlv+F0XvpMvYSl2VmuwGDsywREHuqVx1/t/Gs" +
      "d2U7AYJwVWLc1goxaIRYYDF1HPtefq4FvsLskeVR8J" +
      "i2d8Mpmz+PIXr7j5kSB447xSxGXj+mlf5nRoNDXbl2" +
      "X+g9y2Ps9A5PhG8l1P+5xToz0Dvg5T8FHeHthL9szL" +
      "bqnJnaNa911MD2coOXI4r/EuUy+vT/Ja7yMYCIqcbP" +
      "iVaIddDMhZdX8VY213Psygdw3TlY5vxBssHQDumMiM" +
      "Svto6D43KGjsI1pmJrD6+GWP6xQoJq84TYMkQe9f8o" +
      "2dY/mIIsBwZkZYQ0YzBINvC2L3NnUy"

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

  it should "encrypt dataset metadata" in {
    Source
      .single(DatasetMetadata.toByteString(datasetMetadata))
      .via(aes.encrypt(metadataSecret = metadataSecret))
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedDatasetMetadata =>
        actualEncryptedDatasetMetadata should be(encryptedDatasetMetadata.decodeFromBase64)
      }
  }

  it should "decrypt dataset metadata" in {
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
