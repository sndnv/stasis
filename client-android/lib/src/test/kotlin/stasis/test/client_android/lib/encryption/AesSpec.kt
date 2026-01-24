package stasis.test.client_android.lib.encryption

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeIn
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
            file = plaintextFile.toString(),
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

        val encryptedDatasetMetadata11 =
            "HmbhwaypOBcEU1AhuKsDjI0+veHe83EmsKgSFnSfI8sd" +
                    "L21k3hGfWZ/nv8j3Wdd1s2aatGFivHDQqIEy/XbEO/" +
                    "t57k/hjMsj1x15u++p9cbXllxAWEvACpr8AtoCehZk" +
                    "I9TankVFir2Yfr/N7tpMdNNw/UmA3Cord1AOZ02eRK" +
                    "mbP7xT4Ppbu17AnAD4zNomR4txkDe7Xdr0eyHqtXsW" +
                    "okosNgSd+QZXe/G374c1MGOMbBLfPICjxfxCUOkgG/" +
                    "1pogMMHNTQM8HrwNqweP1h4n9eEjoK4fQpcSwwwspG" +
                    "beplgBGjNJTZDGN5N83TMunCcgk5P5KQTKXh77Mpyv" +
                    "pALCOEHukCm0dWCyqGSGAUKsl0oP3zCoBxY+gTsnHo" +
                    "ock2RLli8jS2KLGt93LeQDuu3sM="

        val encryptedDatasetMetadata17 =
            "HmbhwaypOBcErFAhuKsDjI0+veHe83EmsKgSFnSfI8sd" +
                    "L21k3hGfWZ/nv8j3Wdd1s2aatGFivHDQqIEy/XbEO/" +
                    "t57k/hjMsj1x15u++p9cbXllxAWEvACpr8AtoCehZk" +
                    "I9TankVFir2Yfr/N7tpMdNNw/UmA3Cord1AOZ02eRK" +
                    "mbP7xT4Ppbu17AnAD4zNomR4txkDe7Xdr0eyHqtXsW" +
                    "okosNgSd+QZXe/G374c1MGOMbBLfPICjxfxCUOkgG/" +
                    "1pogMMHNTQM8HrwNqweP1h4n9eEjoK4fQpcSwwwspG" +
                    "beplgBGjNJTZDGN5N83TMunCcgk5P5KQTKXh77Mpyv" +
                    "pALCOEHukCm0dWCyqGSGAUKsl0oP3zCoBxY+gTsnHo" +
                    "ock2RGiIPfENMtteYpnaF228tkE="

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

            actualEncryptedDatasetMetadata shouldBeIn (listOf(
                encryptedDatasetMetadata11.decodeBase64()!!.utf8(),
                encryptedDatasetMetadata17.decodeBase64()!!.utf8()
            ))
        }

        "decrypt dataset metadata" {
            val expectedDatasetMetadata11 = Aes
                .decrypt(Buffer().write(encryptedDatasetMetadata11.decodeBase64()!!), metadataSecret = metadataSecret)
                .buffer()
                .readByteString()
                .toDatasetMetadata()
                .get()

            val expectedDatasetMetadata17 = Aes
                .decrypt(Buffer().write(encryptedDatasetMetadata11.decodeBase64()!!), metadataSecret = metadataSecret)
                .buffer()
                .readByteString()
                .toDatasetMetadata()
                .get()

            expectedDatasetMetadata11 shouldBe (datasetMetadata)
            expectedDatasetMetadata17 shouldBe (datasetMetadata)
        }
    }
})
