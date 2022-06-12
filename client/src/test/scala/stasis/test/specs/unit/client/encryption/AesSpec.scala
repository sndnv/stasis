package stasis.test.specs.unit.client.encryption

import akka.actor.ActorSystem
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
    "C2/oy6GGTHp0fNQitirONsySgkCbV7diShOwLU7Trm0/E" +
      "xBqb2nGQH4+zMhpfNCBVptVM2V9koanOe8kvP1SccFb" +
      "OLY9SwNrXO0W2j7qNUzx4oZ2SQMN2EQJrQ2Rus7+CbN" +
      "ST4Tcqu1x18BucsSRPm6YfDjhUpT7NsCy2vQM/2ufws" +
      "evNM3U//1kqURSpNVcpjRJ5RJW+iZy4CMfI2lcxuWUb" +
      "SSsPbDL2NSgV1g+x5FuUwDbEvtc9ww0BJWcW5yXzWCA" +
      "XOEWJqaPSXwBnF34dlqrGzfOTv5Cc2jg+a3uEcUM6mY" +
      "pM3PxfkJwEaPSqEyP9zFRZKt6W8UuTaxOqpa1PPmcME" +
      "SCYJobf4UWafUFe9GGJ3Jwr4cEYYqrYglmkNlhThajy" +
      "604ewvBfKUUgy0vC+d2U3gX5nrBhTGmn8rCSABj1HXK" +
      "Qm+gNOMxHDW65KAThmX2rnGj3agK7WQv6fccnmveVCj" +
      "xh+U89lHXs9serf05hFC1bf/IzkrpgQOzBjVwIZHJgl" +
      "Xif1WfK7A6RVK/Ii4S+RDEY4z1qyliwjmNMrawBCwX+" +
      "0Y9agTEHtLkwqq+u9J8xue49/eJdQA3Jx9/0Gbesabx" +
      "LTZHD7Ry3isyVMgmG1gLHgmBdvubO1VRMj9mb+XKf+7" +
      "PueHC5ghUzIiwXnt+UIgtdSIJCfWAfWYqpe6PMSkysC" +
      "Ev4XOEy0WGmscYtHYMQinmVQe3ClVORJ9/mbolmc01O" +
      "Jj4M7176G+IlbUHOlnAAZ6O+Ug0ehuc1TSXT0LKKtz4" +
      "nzNsDkvUCXI5VZI="

  private val metadataSecret = DeviceMetadataSecret(
    iv = encryptionIv.decodeFromBase64,
    key = encryptionKey.decodeFromBase64
  )
}
