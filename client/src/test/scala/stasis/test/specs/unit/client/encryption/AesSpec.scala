package stasis.test.specs.unit.client.encryption

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.model.DatasetMetadata
import stasis.client.model.FilesystemMetadata
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers

class AesSpec extends AsyncUnitSpec with EncodingHelpers with ResourceHelpers {
  "An AES encoder/decoder implementation" should "encrypt files" in {
    for {
      actualEncryptedContent <-
        FileIO
          .fromPath(plaintextFile)
          .via(Aes.encrypt(fileSecret = fileSecret))
          .runFold(ByteString.empty)(_ concat _)
      expectedEncryptedContent <-
        FileIO
          .fromPath(encryptedFile)
          .runFold(ByteString.empty)(_ concat _)
    } yield {
      actualEncryptedContent should be(expectedEncryptedContent)
    }
  }

  it should "decrypt files" in {
    for {
      actualDecryptedContent <-
        FileIO
          .fromPath(encryptedFile)
          .via(Aes.decrypt(fileSecret = fileSecret))
          .runFold(ByteString.empty)(_ concat _)
      expectedDecryptedContent <-
        FileIO
          .fromPath(plaintextFile)
          .runFold(ByteString.empty)(_ concat _)
    } yield {
      actualDecryptedContent should be(expectedDecryptedContent)
    }
  }

  it should "encrypt dataset metadata" in {
    Source
      .single(DatasetMetadata.toByteString(datasetMetadata))
      .mapAsync(parallelism = 1)(identity)
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
      .flatMap(rawMetadata => DatasetMetadata.fromByteString(rawMetadata))
      .map { expectedDatasetMetadata =>
        expectedDatasetMetadata should be(datasetMetadata)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "AesSpec")

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
    "HmbhwaypOBcEU1AhuKsDjI0+veHe83EmsKgSFnSfI8sdL" +
      "21k3hGfWZ/nv8j3Wdd1s2aatGFivHDQqIEy/XbEO/t5" +
      "7k/hjMsj1x15u++p9cbXllxAWEvACpr8AtoCehZkI9T" +
      "ankVFir2Yfr/N7tpMdNNw/UmA3Cord1AOZ02eRKmbP7" +
      "xT4Ppbu17AnAD4zNomR4txkDe7Xdr0eyHqtXsWokosN" +
      "gSd+QZXe/G374c1MGOMbBLfPICjxfxCUOkgG/1pogMM" +
      "HNTQM8HrwNqweP1h4n9eEjoK4fQpcSwwwspGbeplgBG" +
      "jNJTZDGN5N83TMunCcgk5P5KQTKXh77MpyvpALCOEHu" +
      "kCm0dWCyqGGDT/74gaoP3z9YCNYFQYB5RO4ss2gQAsq" +
      "fuE9Wnqy2x6mn+gqQ=="

  private val metadataSecret = DeviceMetadataSecret(
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )
}
