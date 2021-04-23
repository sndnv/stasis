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
