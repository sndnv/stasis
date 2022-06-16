package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
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
import java.io.EOFException
import java.util.UUID

class DatasetMetadataSpec : WordSpec({
    "DatasetMetadata" should {
        val secretsConfig: Secret.Config = Secret.Config(
            derivation = Secret.Config.DerivationConfig(
                encryption = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test"),
                authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test")
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

        val serializedDatasetMetadata =
            "CoIBCg0vdG1wL2ZpbGUvb25lEnEKbwoNL3RtcC9maWxl" +
                    "L29uZRABKICokKOB4vjH/wEw//HV1K+ahzg6BHJvb3" +
                    "RCBHJvb3RKCXJ3eHJ3eHJ3eFIBAVooCg8vdG1wL2Zp" +
                    "bGUvb25lXzASFQi4hY2FuP2+zzIQyvep/8C3nu6xAW" +
                    "IEbm9uZRKWAQoNL3RtcC9maWxlL3R3bxKEAQqBAQoN" +
                    "L3RtcC9maWxlL3R3bxACGg8vdG1wL2ZpbGUvdGhyZW" +
                    "Uo//HV1K+ahzgwgKiQo4Hi+Mf/AToEcm9vdEIEcm9v" +
                    "dEoJcnd4cnd4cnd4UgEqWikKDy90bXAvZmlsZS90d2" +
                    "9fMBIWCISG1dThqqq55gEQuvmQh4+DpfiKAWIEZ3pp" +
                    "cBJXChIvdG1wL2RpcmVjdG9yeS9vbmUSQRI/ChIvdG" +
                    "1wL2RpcmVjdG9yeS9vbmUggKiQo4Hi+Mf/ASj/8dXU" +
                    "r5qHODIEcm9vdDoEcm9vdEIJcnd4cnd4cnd4EmgKEi" +
                    "90bXAvZGlyZWN0b3J5L3R3bxJSElAKEi90bXAvZGly" +
                    "ZWN0b3J5L3R3bxIPL3RtcC9maWxlL3RocmVlIP/x1d" +
                    "Svmoc4KICokKOB4vjH/wEyBHJvb3Q6BHJvb3RCCXJ3" +
                    "eHJ3eHJ3eBpeChMKDS90bXAvZmlsZS9vbmUSAgoACh" +
                    "MKDS90bXAvZmlsZS90d28SAhoAChgKEi90bXAvZGly" +
                    "ZWN0b3J5L29uZRICCgAKGAoSL3RtcC9kaXJlY3Rvcn" +
                    "kvdHdvEgIKAA=="

        val encryptedDatasetMetadata =
            "qu7AdPwACWArwpK16YuCXDHYU5Pt01++jHjDWVAtpMqU" +
                    "Vz1X9+HTlPnsnosFiFhItNFAS9qkEgYTWeGKq2cTUV" +
                    "CtCAuQVfl0okzr/nhL2GZhSVHRbGOs8oJ9+JoIXfzn" +
                    "9iDfrHANLHgMfFn0oG9vxjfNSz7j3t9hC6pO3Ie+t1" +
                    "s01Rg7PoLKsosCmWGtJnwtSE+RR+0A6OU0J2fg9IVX" +
                    "lPuxpBS/waUCVkN1zoRLQr+FVXNdYHRb6hsLxGzRpt" +
                    "QCb/x0TDjl2Lt2suaYX+FSdgjTdkvFJRqUm7gwFuCM" +
                    "HJHskoIx6PPhQA3noi+Cr6ZRX+SYqlQ7AfQACogHip" +
                    "M7Y4Kx/tzRJI/Ydq3My4ioQHJwfLrfYZaNny4SI39l" +
                    "5sCa/5kqst0YL3QGLuY3ayVuWYTOx4Sr2ZqYqOvCZi" +
                    "qmPIBWg/QnN6KDEaTdqv37/0gCtVy8BwMT4lYdVJ0S" +
                    "+fes9tM6PQITvAkGojkWLXLEXaBpuYa2YioayX5XAJ" +
                    "sLdBT5M6S8P1tZ55pNn8YTyirl54/j1sSi9s7FttKr" +
                    "wUw7De2dSTiMzTdzom30rPiEKgvVwALJlHTS7YZyvH" +
                    "fBRrBiS8hKu5za0b1mZOHmqqN0Oi3htnbgmTnoNAsi" +
                    "a5YV2igGI6ZsXVHLzSf5X1fqMO8XJ+W+nWJiph6Fiv" +
                    "V7XhaMF0YxmJK6hQM/mtQmiVgBhQiey7yIpjMzdB5U" +
                    "8tIisOvaHdoE4lh0lxzqR5woeo5pIo4+oedlYpW81a" +
                    "R/RPpfGBs6vDGidQBeiZ+fnZHN4JeWwwQ="

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

            metadata.contentChangedBytes shouldBe(Fixtures.Metadata.FileOneMetadata.size)

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

            val fileOneMetadata = currentMetadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
            val fileTwoMetadata = currentMetadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)

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
            datasetMetadata.toByteString() shouldBe (serializedDatasetMetadata.decodeBase64())
        }

        "be deserializable from a valid byte string" {
            serializedDatasetMetadata.decodeBase64()?.toDatasetMetadata() shouldBe (Success(datasetMetadata))
        }

        "fail to be deserialized from an invalid byte string" {
            shouldThrow<EOFException> {
                serializedDatasetMetadata.decodeBase64()?.toAsciiLowercase()?.toDatasetMetadata()?.get()
            }
        }

        "be encryptable" {
            val encrypted = DatasetMetadata
                .encrypt(
                    metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
                    metadata = datasetMetadata,
                    encoder = Aes
                )

            encrypted shouldBe (encryptedDatasetMetadata.decodeBase64())
        }

        "be decryptable" {
            val decrypted = DatasetMetadata
                .decrypt(
                    metadataCrate = metadataCrate,
                    metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
                    metadata = encryptedDatasetMetadata.decodeBase64()?.let { Buffer().write(it) },
                    decoder = Aes
                )

            decrypted shouldBe (datasetMetadata)
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
