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
  "An AES encoder/decoder implementation" should "encrypt files" in {
    for {
      actualEncryptedContent <- FileIO
        .fromPath(plaintextFile)
        .via(Aes.encrypt(fileSecret = fileSecret))
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
        .via(Aes.decrypt(fileSecret = fileSecret))
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
      .via(Aes.encrypt(metadataSecret = metadataSecret))
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedDatasetMetadata =>
        actualEncryptedDatasetMetadata should be(encryptedDatasetMetadata.decodeFromBase64)
      }
  }

  it should "decrypt dataset metadata" in {
    Source
      .single(encryptedDatasetMetadata.decodeFromBase64)
      .via(Aes.decrypt(metadataSecret = metadataSecret))
      .runFold(ByteString.empty)(_ concat _)
      .flatMap(rawMetadata => Future.fromTry(DatasetMetadata.fromByteString(rawMetadata)))
      .map { expectedDatasetMetadata =>
        expectedDatasetMetadata should be(datasetMetadata)
      }
  }

  it should "generate AES keys" in {
    Aes.generateKey().size should be(16)
  }

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
    contentChanged = Map(
      Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
    ),
    metadataChanged = Map(
      Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata,
      Fixtures.Metadata.DirectoryOneMetadata.path -> Fixtures.Metadata.DirectoryOneMetadata,
      Fixtures.Metadata.DirectoryTwoMetadata.path -> Fixtures.Metadata.DirectoryTwoMetadata
    ),
    filesystem = FilesystemMetadata(
      entities = Map(
        Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
        Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated,
        Fixtures.Metadata.DirectoryOneMetadata.path -> FilesystemMetadata.EntityState.New,
        Fixtures.Metadata.DirectoryTwoMetadata.path -> FilesystemMetadata.EntityState.New
      )
    )
  )

  private val encryptedDatasetMetadata =
    "C5HjzIPdVWcrNdsnv2CON8fl+zv4MrBAEQqtcgfcq2R1" +
      "UxFhGnjv6FYG/+oKZu+5qKqaPUF86bO6hu0ayuBSav" +
      "cdTqs9UD0oJ+gZ0DvlP0n+yNV2EnEv3WRStBDO88H7" +
      "APkSTo/mxc92ynBTesysey3pgRo7b8qtCbebqqu87I" +
      "eapqXuJbIy0ZEA6lUQoI1V3SUmsWwsukCbkQb4Sw80" +
      "xuWOchvITtOBg82nFy5VsftuUwXZWPgdZJjOpFJ0uc" +
      "GPsjnFK89uWVlAyO6VTbN1+t9QaGKlSNskdV2c+bX5" +
      "F/h9yhB7Gy2DAxp8LtLXlAzpnTFRfrQKDb5UIAe8CC" +
      "0LpkEz3sEUD7dqUqAsKgyfwDpBqu/cfhL+T4zGdxZs" +
      "lKREPSvj0aVaFX2XBNJYgy8tXaxwT3hm0weM9gzmhc" +
      "KS58yaBZFLzsctTOShg4VOy5IEHdYQJNkzqLrxbt6U" +
      "Mi+S1C6iVDDmgahAllay1bZjsvpukk6/euvEs1CavF" +
      "iwBFcNV6ix9RnifVfJYK0/TyOZXwxhxEvHYe7V4DR9" +
      "13WfNKisBCwI5jFXZY9YuyktMUHjvCa8JCFcNfC/X9" +
      "Wqueool5JMJXcfoLrC+cc3oisqQ85rZzA6e27JcqaY" +
      "LlcJLCR9ctD7G5jXtsS2gWtxp86oViQ+WIRTEgd9dI" +
      "mYclNewoaqRW8quH508nXkrDzy/61riTYWSkvLO27P" +
      "dzwoBJlrxvovla1SUeydWc5Gs2yK95hpU6Q152SZca" +
      "MsRiJdYc37jFo="

  private val metadataSecret = DeviceMetadataSecret(
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )
}
