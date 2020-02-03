package stasis.test.specs.unit.client.model

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import stasis.client.encryption.secrets.{DeviceSecret, Secret}
import stasis.client.encryption.Aes
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{EncodingHelpers, Fixtures}

import scala.util.{Failure, Success}
import scala.util.control.NonFatal

class DatasetMetadataSpec extends AsyncUnitSpec with EncodingHelpers {
  "A DatasetMetadata" should "be serializable to byte string" in {
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
    contentChanged = Map(Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata),
    metadataChanged = Map(Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata),
    filesystem = FilesystemMetadata.empty
  )

  private val serializedDatasetMetadata =
    "CmEKDS90bXAvZmlsZS9vbmUSUAoNL3RtcC9maWxlL29u" +
      "ZRABKICokKOB4vjH/wEw//HV1K+ahzg6BHJvb3RCBH" +
      "Jvb3RKA3J3eFIBAVoVCLiFjYW4/b7PMhDK96n/wLee" +
      "7rEBEnMKDS90bXAvZmlsZS90d28SYgoNL3RtcC9maW" +
      "xlL3R3bxACGg8vdG1wL2ZpbGUvdGhyZWUo//HV1K+a" +
      "hzgwgKiQo4Hi+Mf/AToEcm9vdEIEcm9vdEoDeHdyUg" +
      "EqWhYIhIbV1OGqqrnmARC6+ZCHj4Ol+IoBGgA="

  private val encryptedDatasetMetadata =
    "qg3Lc95bEH10i52w4MHCXTqvEejqkyHe0yPIQBMu4smf" +
      "HUI4unF6LNrF7NBDlaG/tCGlYITrQZGz2qvfwGE+Ol" +
      "aAYw21Of9J02ydh1ApqKyTvIZokIdLyp2Ye16Hsi0Q" +
      "dPTx0W1ifmdqBCFjQ4uGG+UH82PhdiLF2x6UMkZp31" +
      "VVlAMiNIBeqY4gwniweTUiTUbbHPEF4pKY2Zy0ISDA" +
      "PLfsVJNJC0iFgfD9oK5NKt/FVVwpPX1d8jQnyHPRkb" +
      "AAyhupHBGqi2LsmPfu/XAKd1+r+PJkr//69foBc3oV" +
      "QG6vCNeLHDgZGnZ+"
}
