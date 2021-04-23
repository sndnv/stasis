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
            "CnwKDS90bXAvZmlsZS9vbmUSawppCg0vdG1wL2ZpbGUv" +
                    "b25lEAEogKiQo4Hi+Mf/ATD/8dXUr5qHODoEcm9vdE" +
                    "IEcm9vdEoJcnd4cnd4cnd4UgEBWigKDy90bXAvZmls" +
                    "ZS9vbmVfMBIVCLiFjYW4/b7PMhDK96n/wLee7rEBEo" +
                    "4BCg0vdG1wL2ZpbGUvdHdvEn0KewoNL3RtcC9maWxl" +
                    "L3R3bxACGg8vdG1wL2ZpbGUvdGhyZWUo//HV1K+ahz" +
                    "gwgKiQo4Hi+Mf/AToEcm9vdEIEcm9vdEoJcnd4cnd4" +
                    "cnd4UgEqWikKDy90bXAvZmlsZS90d29fMBIWCISG1d" +
                    "Thqqq55gEQuvmQh4+DpfiKARJXChIvdG1wL2RpcmVj" +
                    "dG9yeS9vbmUSQRI/ChIvdG1wL2RpcmVjdG9yeS9vbm" +
                    "UggKiQo4Hi+Mf/ASj/8dXUr5qHODIEcm9vdDoEcm9v" +
                    "dEIJcnd4cnd4cnd4EmgKEi90bXAvZGlyZWN0b3J5L3" +
                    "R3bxJSElAKEi90bXAvZGlyZWN0b3J5L3R3bxIPL3Rt" +
                    "cC9maWxlL3RocmVlIP/x1dSvmoc4KICokKOB4vjH/w" +
                    "EyBHJvb3Q6BHJvb3RCCXJ3eHJ3eHJ3eBpeChMKDS90" +
                    "bXAvZmlsZS9vbmUSAgoAChMKDS90bXAvZmlsZS90d2" +
                    "8SAhoAChgKEi90bXAvZGlyZWN0b3J5L29uZRICCgAK" +
                    "GAoSL3RtcC9kaXJlY3RvcnkvdHdvEgIKAA=="

        val encryptedDatasetMetadata =
            "qhDLc95bEH10i52w4MHCXTqvKuiOtlic12HeBhkiocPe" +
                    "FzxcgvD6PNHUralmkmdwSuCPRf6laTMO5uO03XoTSm" +
                    "brfhaQTsc32Unk9H1E0mNuYwLRNxGO96Im4YdXFPPi" +
                    "/2qfrXs3Q1oLYenJqGdSg3S8thw544E3NN1nrNgOpL" +
                    "cxsXp6L/0snOdm2nDvIiQkM17+E5N6qIPdVkIHnOM/" +
                    "lPuruyvbssZIDVpyjvIgNNWFVXZfKncaeY/xZKs5RI" +
                    "kaEKUxOxadp0S5M3QMjg/f+o0oBR6uIz/ynY1MFvib" +
                    "GqydsvRjwK2TPVXrnV6Hk+Y3NeSYsEtLV496ZyP1KC" +
                    "iF+ToeEFlHS6KpW4j2iHEy+5m38SdzsAN3sSh/NmBv" +
                    "4r2/jKRqqNV6QQJQVpF7aydsD8/I24Ta7OfV29aCfC" +
                    "L2k0yvUhCmuwoOaaNNNU0P0HoVLu9ajauDl0Tm1yep" +
                    "Ii8ivJZGPRoEukR6wj5zSx+5Qqc+r5i8dT4WtGQkPc" +
                    "AIdnaERZ3ESBdZ5Zgb1NsWwFvDmq2Q65+h9Kzl/c+0" +
                    "1AApC/OBSTiT0EAZreZoCQNN2eCIx/YJdrI2L4FElq" +
                    "Jc2EU1DDzYL000XDHjkpKj1qNsLSusyh7R/F6gMFYh" +
                    "fpRNxDMdPpNdOSfTwgKNODTPW6kPL7r+lW4cwTvx94" +
                    "ljUSP4cC4U7NSijVxkidJG7iF14GLt9vySrlEeGncs" +
                    "j7tE8O3OQpoO7jgT/miPLe8VIY1rQKNQyKbPsBHSI3" +
                    "V2c8BqNGNyUDA="

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

            fileOneFailure.message shouldBe ("Metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath()}] not found")

            val fileTwoFailure = shouldThrow<IllegalArgumentException> {
                metadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
            }

            fileTwoFailure.message shouldBe ("Metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath()}] not found")

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
                override suspend fun datasetMetadata(entry: DatasetEntryId): DatasetMetadata {
                    val defaultMetadata = super.datasetMetadata(entry)
                    return if (entry == previousEntry) {
                        previousMetadata
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
