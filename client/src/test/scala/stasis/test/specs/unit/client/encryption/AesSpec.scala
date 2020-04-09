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
    "C4TjzIPdVWcrNdsnv2CON8flyDvHMrBAEQqtcgfcq2R1" +
      "UxFhGnjv6FYG/+oKZu+5qKqaPUF86bO6hu0ayuBSav" +
      "cdTqs9UD0oJ+gZ0DvlP0n+yNV2Ekwtas6rXNgcK2el" +
      "dRyKiRV5QkONc8nEjEMZqedLwyWX8Q835nRyKwjX9G" +
      "GRoYe1PK9tmJ4F4x9Lo5UqzUIj5RJM5RuQiEX7DRc5" +
      "m/ScNfQ7gQgBbSfvCMiUTT3AxZVswpwPnxt0H4msOo" +
      "vQXeUfgTz72ezIsabmR44bpMdCEolM6UXMrYBdbcOR" +
      "33aVH57S5PwDDSdfSbDXhgj113pMe742ScMwUaxVs5" +
      "z4Tfm1MtIrwSOK+kvG/ZF2tUdk2dKcZBqu4EA/pvLt" +
      "GAzJRSxzThWc+/UvNvJzhS0yBvIdT3Jq0yr3/VTxmd" +
      "DKFRNytHijJC+mILxxFjnoAWv3SpZtecIrs5KnDuHx" +
      "VELvyyn1Qi7slrxM60zB6O1gsJhO2VOgb6fWtU6GvF" +
      "ivGSA6ZQdKMuI5iqDeLETluuNs0obfFD6CCo61oDQq" +
      "oyiWMrCLIioX7CZFPQLeFufcodXRmYlg2fLysWEUxf" +
      "v30/1V+ux2KxZ6wuH0ncVvvDAxXpNoaEV2c2fDdZHm" +
      "UQhSJz0+c5bmEJ6x27zPpBcVz/PeMVQbJuctWFxkc8" +
      "nuGTs7q+rPJTlxoXk0hB6cyZ/6ioMgaV+j3U5Y2ooc" +
      "ynk="

  private val metadataSecret = DeviceMetadataSecret(
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )
}
