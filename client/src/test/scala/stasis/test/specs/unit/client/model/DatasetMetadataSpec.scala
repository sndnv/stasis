package stasis.test.specs.unit.client.model

import java.util.UUID
import java.util.zip.ZipException

import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.model.FilesystemMetadata
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient

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
      _ <-
        metadata
          .collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
          .map { result =>
            fail(s"Unexpected result received: [$result]")
          }
          .recover { case NonFatal(e: IllegalArgumentException) =>
            e.getMessage should be(
              s"Metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] not found"
            )
          }
      _ <-
        metadata
          .collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
          .map { result =>
            fail(s"Unexpected result received: [$result]")
          }
          .recover { case NonFatal(e: IllegalArgumentException) =>
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
      _ <- currentMetadata.collect(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient).failed.map {
        case NonFatal(e: IllegalArgumentException) =>
          e.getMessage should be(
            s"Expected metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] " +
              s"but none was found in metadata for entry [$previousEntry]"
          )
      }
      _ <- currentMetadata.collect(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient).failed.map {
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
      _ <-
        metadata
          .require(entity = Fixtures.Metadata.FileOneMetadata.path, api = mockApiClient)
          .map { result =>
            fail(s"Unexpected result received: [$result]")
          }
          .recover { case NonFatal(e: IllegalArgumentException) =>
            e.getMessage should be(
              s"Required metadata for entity [${Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath}] not found"
            )
          }
      _ <-
        metadata
          .require(entity = Fixtures.Metadata.FileTwoMetadata.path, api = mockApiClient)
          .map { result =>
            fail(s"Unexpected result received: [$result]")
          }
          .recover { case NonFatal(e: IllegalArgumentException) =>
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
    DatasetMetadata.toByteString(metadata = datasetMetadata).map { serialized =>
      serialized should be(serializedDatasetMetadata.decodeFromBase64)
    }
  }

  it should "be deserializable from a valid byte string" in {
    DatasetMetadata.fromByteString(bytes = serializedDatasetMetadata.decodeFromBase64).map { deserialized =>
      deserialized should be(datasetMetadata)
    }
  }

  it should "fail to be deserialized from an invalid byte string" in {
    DatasetMetadata
      .fromByteString(bytes = serializedDatasetMetadata.decodeFromBase64.take(42))
      .map(metadata => fail(s"Unexpected successful result received: [$metadata]"))
      .failed
      .map { e => e shouldBe a[ZipException] }
  }

  it should "be encryptable" in {
    DatasetMetadata
      .encrypt(
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
      .recoverWith { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(s"Cannot decrypt metadata crate [$metadataCrate]; no data provided")
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataPushSpec")

  private implicit val secretsConfig: SecretsConfig = SecretsConfig(
    derivation = SecretsConfig.Derivation(
      encryption = SecretsConfig.Derivation.Encryption(
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      ),
      authentication = SecretsConfig.Derivation.Authentication(
        enabled = true,
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      )
    ),
    encryption = SecretsConfig.Encryption(
      file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 16),
      metadata = SecretsConfig.Encryption.Metadata(keySize = 24, ivSize = 32),
      deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 32, ivSize = 64)
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
    "H4sIAAAAAAAAAOJqYuTi1S/JLdBPy8xJ1c/PSxUq5MpH" +
      "ExJg1GhYMWFx46Mfx/8zGvz/ePXK+lntFlYsRfn5JU" +
      "5g0ouzqLwCgoIYGaM0uPhRDIg3EBLl2NHa27rj777z" +
      "RgKnvq/8f2D7vHcbGZNY8kBWTkNxREl5vlALI1cjuq" +
      "AAkxSSsSUZRampGjDHGCAciNNdWlGayO4Cmgh0lxhH" +
      "S9vVKw9Xrdr5jFFg188J7f3NS390AR2WXpVZIBTOJQ" +
      "TWkJJZlJpckl9UCQ4hRyF7bOIKCDfA3WUEdgbUSQjH" +
      "CGVgGADydZBQAFZxdG8rwIxHihccNknFcQmjxzATFw" +
      "OqIMgOJikGLgms3gUpl8DqLqAMAAAA//8DALy3AudB" +
      "AgAA"

  private val encryptedDatasetMetadata =
    "v+fJfvEvfQ1b7Ra25wpP5nB0bDKod5n6dsNhYmphKWy2" +
      "a0BZRpmKjRg17YubrV+8USyPzN67PPBkyI+c6uyFG2" +
      "qP3vJMkjE8KbyEn6kIGOxHPYvnfSthIFyIV02bnSR9" +
      "3EdXfbGUDCjl1SZXPHGyjIolyk+CUGGxSjryYT4sDJ" +
      "kwKGPH6rVF9iimrCUHTnNXqfCpMsjtTxmyvGUVYpcd" +
      "8FQJ/zSOBROe9WZidltAtU1namFZTg+k2Ot9kBBt5r" +
      "X8AJ/4DA0jzdwSO0Apu0HL4i0mf0YBihD/mfzgLYck" +
      "YL6F+PW77xTJMizuhEGDNQMc2tzw8W3RFpTPqJdg6v" +
      "/Oc1ip0HFR31KVAgJcuklS1Cvs+zVcO5EEnvChRuJN" +
      "lNLNb4720g9gwdJpZ7U3dU1mZw=="
}
