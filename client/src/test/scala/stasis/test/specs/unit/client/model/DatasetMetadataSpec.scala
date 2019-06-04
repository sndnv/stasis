package stasis.test.specs.unit.client.model

import com.google.protobuf.InvalidProtocolBufferException
import stasis.client.model.DatasetMetadata
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.{EncodingHelpers, Fixtures}

import scala.util.{Failure, Success}

class DatasetMetadataSpec extends UnitSpec with EncodingHelpers {
  private val datasetMetadata = DatasetMetadata(
    contentChanged = Seq(Fixtures.Metadata.FileOneMetadata),
    metadataChanged = Seq(Fixtures.Metadata.FileTwoMetadata)
  )

  private val serializedDatasetMetadata =
    "ClAKDS90bXAvZmlsZS9vbmUQASiAqJCjgeL4x/8BMP/x" +
      "1dSvmoc4OgRyb290QgRyb290SgNyd3hSAQFaFQi4hY" +
      "2FuP2+zzIQyvep/8C3nu6xARJiCg0vdG1wL2ZpbGUv" +
      "dHdvEAIaDy90bXAvZmlsZS90aHJlZSj/8dXUr5qHOD" +
      "CAqJCjgeL4x/8BOgRyb290QgRyb290SgN4d3JSASpa" +
      "FgiEhtXU4aqqueYBELr5kIePg6X4igE="

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
}
