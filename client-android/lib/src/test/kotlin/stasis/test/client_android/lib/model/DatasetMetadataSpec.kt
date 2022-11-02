package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.DatasetMetadata.Companion.toByteString
import stasis.client_android.lib.model.DatasetMetadata.Companion.toDatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.io.IOException
import java.util.UUID

class DatasetMetadataSpec : WordSpec({
    "DatasetMetadata" should {
        val secretsConfig: Secret.Config = Secret.Config(
            derivation = Secret.Config.DerivationConfig(
                encryption = Secret.EncryptionKeyDerivationConfig(
                    secretSize = 64,
                    iterations = 100000,
                    saltPrefix = "unit-test"
                ),
                authentication = Secret.AuthenticationKeyDerivationConfig(
                    enabled = true,
                    secretSize = 64,
                    iterations = 100000,
                    saltPrefix = "unit-test"
                )
            ),
            encryption = Secret.Config.EncryptionConfig(
                file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
                metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
                deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
            )
        )

        val user = UUID.fromString("1e063fb6-7342-4bf0-8885-2e7d389b091e")
        val device = UUID.fromString("383e5974-9d8c-40b9-bbab-c648b43db191")
        val metadataCrate = UUID.fromString("559f857a-c973-4848-8a38-261a3f39e0fd")

        val deviceSecret = DeviceSecret(
            user = user,
            device = device,
            secret = "some-secret".toByteArray().toByteString(),
            target = secretsConfig
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

        val serializedDatasetMetadata11 =
            "H4sIAAAAAAAAAOJqYuTi1S/JLdBPy8xJ1c/PSxUq5MpH" +
                    "ExJg1GhYMWFx46Mfx/8zGvz/ePXK+lntFlYsRfn5JU" +
                    "5g0ouzqLwCgoIYGaM0uPhRDIg3EBLl2NHa27rj777z" +
                    "RgKnvq/8f2D7vHcbGZNY8kBWTkNxREl5vlALI1cjuq" +
                    "AAkxSSsSUZRampGjDHGCAciNNdWlGayO4Cmgh0lxhH" +
                    "S9vVKw9Xrdr5jFFg188J7f3NS390AR2WXpVZIBTOJQ" +
                    "TWkJJZlJpckl9UCQ4hRyF7bOIKCDfA3WUEdgbUSQjH" +
                    "CGVgGADydZBQAFZxdG8rwIxHihccNknFcQmjxzATFw" +
                    "OqIMgOJikGLgms3gUpx5QB6+FiAAAAAP//AwC8twLn" +
                    "QQIAAA=="

        val serializedDatasetMetadata17 =
            "H4sIAAAAAAAA/+JqYuTi1S/JLdBPy8xJ1c/PSxUq5MpH" +
                    "ExJg1GhYMWFx46Mfx/8zGvz/ePXK+lntFlYsRfn5JU" +
                    "5g0ouzqLwCgoIYGaM0uPhRDIg3EBLl2NHa27rj777z" +
                    "RgKnvq/8f2D7vHcbGZNY8kBWTkNxREl5vlALI1cjuq" +
                    "AAkxSSsSUZRampGjDHGCAciNNdWlGayO4Cmgh0lxhH" +
                    "S9vVKw9Xrdr5jFFg188J7f3NS390AR2WXpVZIBTOJQ" +
                    "TWkJJZlJpckl9UCQ4hRyF7bOIKCDfA3WUEdgbUSQjH" +
                    "CGVgGADydZBQAFZxdG8rwIxHihccNknFcQmjxzATFw" +
                    "OqIMgOJikGLgms3gUpx5QB6+FiAAAAAP//AwC8twLn" +
                    "QQIAAA=="

        val encryptedDatasetMetadata11 =
            "v+fJfvEvfQ1b7Ra25wpP5nB0bDKod5n6dsNhYmphKWy2" +
                    "a0BZRpmKjRg17YubrV+8USyPzN67PPBkyI+c6uyFG2" +
                    "qP3vJMkjE8KbyEn6kIGOxHPYvnfSthIFyIV02bnSR9" +
                    "3EdXfbGUDCjl1SZXPHGyjIolyk+CUGGxSjryYT4sDJ" +
                    "kwKGPH6rVF9iimrCUHTnNXqfCpMsjtTxmyvGUVYpcd" +
                    "8FQJ/zSOBROe9WZidltAtU1namFZTg+k2Ot9kBBt5r" +
                    "X8AJ/4DA0jzdwSO0Apu0HL4i0mf0YBihD/mfzgLYck" +
                    "YL6F+PW77xTJMizuhEGDNQMc2tzw8W3RFpTPqJdg6v" +
                    "/Oc1ip0HFR31KVAgJc6h25EWqC+zVcxJH4nUyq8wfr" +
                    "19DN9VH027ux7U/SoD7qRQue8ao="

        val encryptedDatasetMetadata17 =
            "v+fJfvEvfQ1bEha25wpP5nB0bDKod5n6dsNhYmphKWy2" +
                    "a0BZRpmKjRg17YubrV+8USyPzN67PPBkyI+c6uyFG2" +
                    "qP3vJMkjE8KbyEn6kIGOxHPYvnfSthIFyIV02bnSR9" +
                    "3EdXfbGUDCjl1SZXPHGyjIolyk+CUGGxSjryYT4sDJ" +
                    "kwKGPH6rVF9iimrCUHTnNXqfCpMsjtTxmyvGUVYpcd" +
                    "8FQJ/zSOBROe9WZidltAtU1namFZTg+k2Ot9kBBt5r" +
                    "X8AJ/4DA0jzdwSO0Apu0HL4i0mf0YBihD/mfzgLYck" +
                    "YL6F+PW77xTJMizuhEGDNQMc2tzw8W3RFpTPqJdg6v" +
                    "/Oc1ip0HFR31KVAgJc6h25EWqC+zVcxJH4nUyq8wfr" +
                    "19DN9T9PHaP5Qd8rXYpCEjtNE8c="

        "retrieve metadata for individual files (new and updated)" {
            val mockApiClient = MockServerApiEndpointClient()

            val metadata = DatasetMetadata(
                contentChanged = mapOf(
                    Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
                ),
                metadataChanged = mapOf(
                    Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata
                ),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated
                    )
                )
            )

            val fileOneMetadata = metadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            val fileTwoMetadata = metadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)

            metadata.contentChangedBytes shouldBe (Fixtures.Metadata.FileOneMetadata.size)

            fileOneMetadata shouldBe (Fixtures.Metadata.FileOneMetadata)
            fileTwoMetadata shouldBe (Fixtures.Metadata.FileTwoMetadata)
        }

        "handle missing metadata when collecting data for individual files (new and updated)" {
            val mockApiClient = MockServerApiEndpointClient()

            val metadata = DatasetMetadata(
                contentChanged = emptyMap(),
                metadataChanged = emptyMap(),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated
                    )
                )
            )

            val fileOneFailure = shouldThrow<IllegalArgumentException> {
                metadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            }

            fileOneFailure
                .message shouldBe ("Metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath()}] not found")

            val fileTwoFailure = shouldThrow<IllegalArgumentException> {
                metadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
            }

            fileTwoFailure
                .message shouldBe ("Metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath()}] not found")

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
        }

        "collect metadata for individual files (existing)" {
            val previousEntry = UUID.randomUUID()

            val currentMetadata = DatasetMetadata(
                contentChanged = emptyMap(),
                metadataChanged = emptyMap(),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.Existing(entry = previousEntry),
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Existing(entry = previousEntry)
                    )
                )
            )

            val previousMetadata = DatasetMetadata(
                contentChanged = mapOf(
                    Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
                ),
                metadataChanged = mapOf(
                    Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata
                ),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated
                    )
                )
            )

            val mockApiClient = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun datasetMetadata(entry: DatasetEntryId): Try<DatasetMetadata> {
                    val defaultMetadata = super.datasetMetadata(entry)
                    return if (entry == previousEntry) {
                        Success(previousMetadata)
                    } else {
                        defaultMetadata
                    }
                }
            }

            val fileOneMetadata =
                currentMetadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            val fileTwoMetadata =
                currentMetadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (2)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)

            fileOneMetadata shouldBe (Fixtures.Metadata.FileOneMetadata)
            fileTwoMetadata shouldBe (Fixtures.Metadata.FileTwoMetadata)
        }

        "handle missing metadata when collecting data for individual files (existing)" {
            val mockApiClient = MockServerApiEndpointClient()

            val previousEntry = UUID.randomUUID()

            val currentMetadata = DatasetMetadata(
                contentChanged = emptyMap(),
                metadataChanged = emptyMap(),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.Existing(entry = previousEntry),
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Existing(entry = previousEntry)
                    )
                )
            )

            val fileOneFailure = shouldThrow<IllegalArgumentException> {
                currentMetadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            }

            fileOneFailure.message shouldBe (
                    "Expected metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath()}] " +
                            "but none was found in metadata for entry [$previousEntry]"
                    )

            val fileTwoFailure = shouldThrow<IllegalArgumentException> {
                currentMetadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
            }

            fileTwoFailure.message shouldBe (
                    "Expected metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath()}] " +
                            "but none was found in metadata for entry [$previousEntry]"
                    )

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (2)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
        }

        "retrieve required metadata for individual files" {
            val mockApiClient = MockServerApiEndpointClient()

            val metadata = DatasetMetadata(
                contentChanged = mapOf(
                    Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
                ),
                metadataChanged = mapOf(
                    Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata
                ),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated
                    )
                )
            )

            val fileOneMetadata = metadata.require(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            val fileTwoMetadata = metadata.require(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)

            fileOneMetadata shouldBe (Fixtures.Metadata.FileOneMetadata)
            fileTwoMetadata shouldBe (Fixtures.Metadata.FileTwoMetadata)
        }

        "fail if required metadata is missing for individual files" {
            val mockApiClient = MockServerApiEndpointClient()

            val metadata = DatasetMetadata.empty()

            val fileOneFailure = shouldThrow<IllegalArgumentException> {
                metadata.require(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            }

            fileOneFailure.message shouldBe (
                    "Required metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath()}] not found"
                    )

            val fileTwoFailure = shouldThrow<IllegalArgumentException> {
                metadata.require(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
            }

            fileTwoFailure.message shouldBe (
                    "Required metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath()}] not found"
                    )

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
        }

        "be serializable to byte string" {
            datasetMetadata.toByteString() shouldBeIn (listOf(
                serializedDatasetMetadata11.decodeBase64(),
                serializedDatasetMetadata17.decodeBase64()
            ))
        }

        "be deserializable from a valid byte string" {
            serializedDatasetMetadata11.decodeBase64()?.toDatasetMetadata() shouldBe (Success(datasetMetadata))
            serializedDatasetMetadata17.decodeBase64()?.toDatasetMetadata() shouldBe (Success(datasetMetadata))
        }

        "fail to be deserialized from an invalid byte string" {
            shouldThrow<IOException> {
                serializedDatasetMetadata11.decodeBase64()?.toAsciiLowercase()?.toDatasetMetadata()?.get()
            }

            shouldThrow<IOException> {
                serializedDatasetMetadata17.decodeBase64()?.toAsciiLowercase()?.toDatasetMetadata()?.get()
            }
        }

        "be encryptable" {
            val encrypted = DatasetMetadata
                .encrypt(
                    metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
                    metadata = datasetMetadata,
                    encoder = Aes
                )

            encrypted shouldBeIn (listOf(
                encryptedDatasetMetadata11.decodeBase64(),
                encryptedDatasetMetadata17.decodeBase64()
            ))
        }

        "be decryptable" {
            val decrypted11 = DatasetMetadata
                .decrypt(
                    metadataCrate = metadataCrate,
                    metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
                    metadata = encryptedDatasetMetadata11.decodeBase64()?.let { Buffer().write(it) },
                    decoder = Aes
                )

            val decrypted17 = DatasetMetadata
                .decrypt(
                    metadataCrate = metadataCrate,
                    metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
                    metadata = encryptedDatasetMetadata17.decodeBase64()?.let { Buffer().write(it) },
                    decoder = Aes
                )

            decrypted11 shouldBe (datasetMetadata)
            decrypted17 shouldBe (datasetMetadata)
        }

        "fail decryption if no encrypted data is provided" {
            val e = shouldThrow<IllegalArgumentException> {
                DatasetMetadata
                    .decrypt(
                        metadataCrate = metadataCrate,
                        metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
                        metadata = null,
                        decoder = Aes
                    )
            }

            e.message shouldBe ("Cannot decrypt metadata crate [$metadataCrate]; no data provided")
        }
    }
})
