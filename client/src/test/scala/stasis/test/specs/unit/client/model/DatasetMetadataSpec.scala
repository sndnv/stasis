package stasis.test.specs.unit.client.model

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, Secret}
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.{EncodingHelpers, Fixtures}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class DatasetMetadataSpec extends AsyncUnitSpec with EncodingHelpers {
  "A DatasetMetadata" should "retrieve metadata for individual files (new and updated)" in {
    val mockApiClient = MockServerApiEndpointClient()

    val metadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      ),
      filesystem = FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated
        )
      )
    )

    for {
      fileOneMetadata <- metadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
      fileTwoMetadata <- metadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)

      fileOneMetadata should be(Some(Fixtures.Metadata.FileOneMetadata))
      fileTwoMetadata should be(Some(Fixtures.Metadata.FileTwoMetadata))
    }
  }

  it should "handle missing metadata when collecting data for individual files (new and updated)" in {
    val mockApiClient = MockServerApiEndpointClient()

    val metadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated
        )
      )
    )

    for {
      _ <- metadata
        .collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
        .map { result =>
          fail(s"Unexpected result received: [$result]")
        }
        .recover {
          case NonFatal(e: IllegalArgumentException) =>
            e.getMessage should be(
              s"Metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] not found"
            )
        }
      _ <- metadata
        .collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
        .map { result =>
          fail(s"Unexpected result received: [$result]")
        }
        .recover {
          case NonFatal(e: IllegalArgumentException) =>
            e.getMessage should be(
              s"Metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath}] not found"
            )
        }
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
    }
  }

  it should "collect metadata for individual files (existing)" in {
    val previousEntry = DatasetEntry.generateId()

    val currentMetadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.Existing(entry = previousEntry),
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Existing(entry = previousEntry)
        )
      )
    )

    val previousMetadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      ),
      filesystem = FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated
        )
      )
    )

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata] =
        super.datasetMetadata(entry).map { defaultMetadata =>
          if (entry == previousEntry) {
            previousMetadata
          } else {
            defaultMetadata
          }
        }
    }

    for {
      fileOneMetadata <- currentMetadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
      fileTwoMetadata <- currentMetadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(2)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)

      fileOneMetadata should be(Some(Fixtures.Metadata.FileOneMetadata))
      fileTwoMetadata should be(Some(Fixtures.Metadata.FileTwoMetadata))
    }
  }

  it should "handle missing metadata when collecting data for individual files (existing)" in {
    val mockApiClient = MockServerApiEndpointClient()

    val previousEntry = DatasetEntry.generateId()

    val currentMetadata = DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.Existing(entry = previousEntry),
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Existing(entry = previousEntry)
        )
      )
    )

    for {
      _ <- currentMetadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient).recover {
        case NonFatal(e: IllegalArgumentException) =>
          e.getMessage should be(
            s"Expected metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] " +
              s"but none was found in metadata for entry [$previousEntry]"
          )
      }
      _ <- currentMetadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient).recover {
        case NonFatal(e: IllegalArgumentException) =>
          e.getMessage should be(
            s"Expected metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath}] " +
              s"but none was found in metadata for entry [$previousEntry]"
          )
      }
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(2)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
    }
  }

  it should "retrieve required metadata for individual files" in {
    val mockApiClient = MockServerApiEndpointClient()

    val metadata = DatasetMetadata(
      contentChanged = Map(
        Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
      ),
      metadataChanged = Map(
        Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata
      ),
      filesystem = FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated
        )
      )
    )

    for {
      fileOneMetadata <- metadata.require(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
      fileTwoMetadata <- metadata.require(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)

      fileOneMetadata should be(Fixtures.Metadata.FileOneMetadata)
      fileTwoMetadata should be(Fixtures.Metadata.FileTwoMetadata)
    }
  }

  it should "fail if required metadata is missing for individual files" in {
    val mockApiClient = MockServerApiEndpointClient()

    val metadata = DatasetMetadata.empty

    for {
      _ <- metadata
        .require(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
        .map { result =>
          fail(s"Unexpected result received: [$result]")
        }
        .recover {
          case NonFatal(e: IllegalArgumentException) =>
            e.getMessage should be(
              s"Required metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] not found"
            )
        }
      _ <- metadata
        .require(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
        .map { result =>
          fail(s"Unexpected result received: [$result]")
        }
        .recover {
          case NonFatal(e: IllegalArgumentException) =>
            e.getMessage should be(
              s"Required metadata for entity [${Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath}] not found"
            )
        }
    } yield {
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
    }
  }

  it should "be serializable to byte string" in {
    DatasetMetadata.toByteString(metadata = datasetMetadata) should be(
      serializedDatasetMetadata.decodeFromBase64
    )
  }

  it should "be deserializable from a valid byte string" in {
    DatasetMetadata.fromByteString(bytes = serializedDatasetMetadata.decodeFromBase64) should be(
      Success(datasetMetadata)
    )
  }

  it should "fail to be deserialized from an invalid byte string" in {
    DatasetMetadata.fromByteString(bytes = serializedDatasetMetadata.decodeFromBase64.take(42)) match {
      case Success(metadata) => fail(s"Unexpected successful result received: [$metadata]")
      case Failure(e)        => e shouldBe a[InvalidProtocolBufferException]
    }
  }

  it should "be encryptable" in {
    DatasetMetadata
      .encrypt(
        metadataCrate = metadataCrate,
        metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
        metadata = datasetMetadata,
        encoder = Aes
      )
      .map { encrypted =>
        encrypted should be(encryptedDatasetMetadata.decodeFromBase64)
      }
  }

  it should "be decryptable" in {
    DatasetMetadata
      .decrypt(
        metadataCrate = metadataCrate,
        metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
        metadata = Some(Source.single(encryptedDatasetMetadata.decodeFromBase64)),
        decoder = Aes
      )
      .map { decrypted =>
        decrypted should be(datasetMetadata)
      }
  }

  it should "fail decryption if no encrypted data is provided" in {
    DatasetMetadata
      .decrypt(
        metadataCrate = metadataCrate,
        metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
        metadata = None,
        decoder = Aes
      )
      .map { other =>
        fail(s"Unexpected result received: [$other]")
      }
      .recoverWith {
        case NonFatal(e: IllegalArgumentException) =>
          e.getMessage should be(s"Cannot decrypt metadata crate [$metadataCrate]; no data provided")
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataPushSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val secretsConfig: Secret.Config = Secret.Config(
    derivation = Secret.DerivationConfig(
      encryption = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test"),
      authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test")
    ),
    encryption = Secret.EncryptionConfig(
      file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
      metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
      deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
    )
  )

  private val user = UUID.fromString("1e063fb6-7342-4bf0-8885-2e7d389b091e")
  private val device = UUID.fromString("383e5974-9d8c-40b9-bbab-c648b43db191")
  private val metadataCrate = UUID.fromString("559f857a-c973-4848-8a38-261a3f39e0fd")

  private val deviceSecret = DeviceSecret(
    user = user,
    device = device,
    secret = ByteString("some-secret")
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

  private val serializedDatasetMetadata =
    "CmkKDS90bXAvZmlsZS9vbmUSWApWCg0vdG1wL2ZpbGUv" +
      "b25lEAEogKiQo4Hi+Mf/ATD/8dXUr5qHODoEcm9vdE" +
      "IEcm9vdEoJcnd4cnd4cnd4UgEBWhUIuIWNhbj9vs8y" +
      "EMr3qf/At57usQESewoNL3RtcC9maWxlL3R3bxJqCm" +
      "gKDS90bXAvZmlsZS90d28QAhoPL3RtcC9maWxlL3Ro" +
      "cmVlKP/x1dSvmoc4MICokKOB4vjH/wE6BHJvb3RCBH" +
      "Jvb3RKCXJ3eHJ3eHJ3eFIBKloWCISG1dThqqq55gEQ" +
      "uvmQh4+DpfiKARJXChIvdG1wL2RpcmVjdG9yeS9vbm" +
      "USQRI/ChIvdG1wL2RpcmVjdG9yeS9vbmUggKiQo4Hi" +
      "+Mf/ASj/8dXUr5qHODIEcm9vdDoEcm9vdEIJcnd4cn" +
      "d4cnd4EmgKEi90bXAvZGlyZWN0b3J5L3R3bxJSElAK" +
      "Ei90bXAvZGlyZWN0b3J5L3R3bxIPL3RtcC9maWxlL3" +
      "RocmVlIP/x1dSvmoc4KICokKOB4vjH/wEyBHJvb3Q6" +
      "BHJvb3RCCXJ3eHJ3eHJ3eBpeChMKDS90bXAvZmlsZS" +
      "9vbmUSAgoAChMKDS90bXAvZmlsZS90d28SAhoAChgK" +
      "Ei90bXAvZGlyZWN0b3J5L29uZRICCgAKGAoSL3RtcC" +
      "9kaXJlY3RvcnkvdHdvEgIKAA=="

  private val encryptedDatasetMetadata =
    "qgXLc95bEH10i52w4MHCXTqvGeixtlic12HeBhkiocPe" +
      "FzxcgvD6PNHUralmkmdwSuCPRf6laTMO5uO03XoTSm" +
      "brfhaQTsc32Unk9H1E0mNuYwLRNyyMQAjfCU+FzFW8" +
      "io8HauGoxNbw2FBeXujnUb4e9COVfUSt2x6OLXtlvF" +
      "E6tlghNuBz1ehj0zq0ITxbIzn7R+0a99jWTwEE2vsy" +
      "yeq5/MQofR3I47A6kRThyBMrw+bqsBMIggxL33Dhx8" +
      "NF/3nrkeUIJ/ExSjx/hDKxpJU6f/VHgqEaRVCNgo7z" +
      "0iJ1Z3rKP3wTM2jI+jyHgeIrf6+FtUF3E/IeFogck5" +
      "l2EoKY/Ep4hTZJ82McX+zbjuSSghozqgsnHuSG54Tu" +
      "bhUy9KP6N2W8r4roZLFQbSVzVJGl247W7Mqu0I6VYD" +
      "CuYZNH4/lOUeKFBfudoPGpGoPmea8n0LCbjGywtxjM" +
      "REJfo5ERKwQOrVB2vyQAdkS6QMUe5IWjYHIEsno4Pc" +
      "AXawGzdzI/j+yCEm8MmDLMNZs2FycuO+rkn8yFvc/j" +
      "oF0gDeumbz6M2lcL9WvupM28SXS64lnVi2GYqxDvDI" +
      "wBslJIYULiISxRPmrV9pD7yLh3MHavxWud9FeqN2Ff" +
      "AcsWzypeP9VAMiG1r3r0HUirM5R5SMrb6w1ii2Do8M" +
      "kVOkudGUJxjIL5lFsk/7k+i6uvXjWeyRzAAyhuj1oa" +
      "4iM="
}
