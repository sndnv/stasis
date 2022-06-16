package stasis.test.client_android.lib.encryption

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.Sink
import okio.buffer
import okio.source
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.DatasetMetadata.Companion.toByteString
import stasis.client_android.lib.model.DatasetMetadata.Companion.toDatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.content

class AesSpec : WordSpec({
    "An AES encoder/decoder implementation" should {
        val plaintextFile = "/encryption/plaintext-file".asTestResource()
        val encryptedFile = "/encryption/encrypted-file".asTestResource()

        val encryptionIv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
        val encryptionKey = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

        val fileSecret = DeviceFileSecret(
            file = plaintextFile,
            iv = encryptionIv.decodeBase64()!!,
            key = encryptionKey.decodeBase64()!!
        )

        val datasetMetadata = DatasetMetadata(
            contentChanged = mapOf(
                Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
            ),
            metadataChanged = mapOf(
                Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata,
                Fixtures.Metadata.DirectoryOneMetadata.path to Fixtures.Metadata.DirectoryOneMetadata,
                Fixtures.Metadata.DirectoryTwoMetadata.path to Fixtures.Metadata.DirectoryTwoMetadata
            ),
            filesystem = FilesystemMetadata(
                entities = mapOf(
                    Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                    Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated,
                    Fixtures.Metadata.DirectoryOneMetadata.path to FilesystemMetadata.EntityState.New,
                    Fixtures.Metadata.DirectoryTwoMetadata.path to FilesystemMetadata.EntityState.New
                )
            )
        )

        val encryptedDatasetMetadata =
            "C2/oy6GGTHp0fNQitirONsySgkCbV7diShOwLU7Trm0/" +
                    "ExBqb2nGQH4+zMhpfNCBVptVM2V9koanOe8kvP1Scc" +
                    "FbOLY9SwNrXO0W2j7qNUzx4oZ2SQMN2EQJrQ2Rus7+" +
                    "CbNST4Tcqu1x18BucsSRPm6YfDjhUpT7NsCy2vQM/2" +
                    "ufwsevNM3U//1kqURSpNVcpjRJ5RJW+iZy4CMfI2lc" +
                    "xuWUbSSsPbDL2NSgV1g+x5FuUwDbEvtc9ww0BJWcW5" +
                    "yXzWCAXOEWJqaPSXwBnF34dlqrGzfOTv5Cc2jg+a3u" +
                    "EcUM6mYpM3PxfkJwEaPSqEyP9zFRZKt6W8UuTaxOqp" +
                    "a1PPmcMESCYJobf4UWafUFe9GGJ3Jwr4cEYYqrYglm" +
                    "kNlhThajy604ewvBfKUUgy0vC+d2U3gX5nrBhTGmn8" +
                    "rCSABj1HXKQm+gNOMxHDW65KAThmX2rnGj3agK7WQv" +
                    "6fccnmveVCjxh+U89lHXs9serf05hFC1bf/IzkrpgQ" +
                    "OzBjVwIZHJglXif1WfK7A6RVK/Ii4S+RDEY4z1qyli" +
                    "wjmNMrawBCwX+0Y9agTEHtLkwqq+u9J8xue49/eJdQ" +
                    "A3Jx9/0GbesabxLTZHD7Ry3isyVMgmG1gLHgmBdvub" +
                    "O1VRMj9mb+XKf+7PueHC5ghUzIiwXnt+UIgtdSIJCf" +
                    "WAfWYqpe6PMSkysCEv4XOEy0WGmscYtHYMQinmVQe3" +
                    "ClVORJ9/mbolmc01OJj4M7176G+IlbUHOlnAAZ6O+U" +
                    "g0ehuc1TSXT0LKKtz4nzNsDkvUCXI5VZI="

        val metadataSecret = DeviceMetadataSecret(
            iv = encryptionIv.decodeBase64()!!,
            key = encryptionKey.decodeBase64()!!
        )

        "encrypt files (source)" {
            val actualEncryptedContent = Aes
                .encrypt(plaintextFile.source(), fileSecret = fileSecret)
                .buffer()
                .readUtf8()

            val expectedEncryptedContent = encryptedFile.content()

            actualEncryptedContent shouldBe (expectedEncryptedContent)
        }

        "encrypt files (sink)" {
            val actualEncryptedContent = Buffer()

            Aes.encrypt(actualEncryptedContent as Sink, fileSecret = fileSecret)
                .buffer()
                .use { it.writeAll(plaintextFile.source()) }

            val expectedEncryptedContent = encryptedFile.content()

            actualEncryptedContent.readUtf8() shouldBe (expectedEncryptedContent)
        }


        "decrypt files" {
            val actualDecryptedContent = Aes
                .decrypt(encryptedFile.source(), fileSecret = fileSecret)
                .buffer()
                .readUtf8()

            val expectedDecryptedContent = plaintextFile.content()

            actualDecryptedContent shouldBe (expectedDecryptedContent)
        }

        "encrypt dataset metadata" {
            val actualEncryptedDatasetMetadata = Aes
                .encrypt(Buffer().write(datasetMetadata.toByteString()), metadataSecret = metadataSecret)
                .buffer()
                .readUtf8()

            actualEncryptedDatasetMetadata shouldBe (encryptedDatasetMetadata.decodeBase64()!!.utf8())
        }

        "decrypt dataset metadata" {
            val expectedDatasetMetadata = Aes
                .decrypt(Buffer().write(encryptedDatasetMetadata.decodeBase64()!!), metadataSecret = metadataSecret)
                .buffer()
                .readByteString()
                .toDatasetMetadata()
                .get()

            expectedDatasetMetadata shouldBe (datasetMetadata)
        }
    }
})
