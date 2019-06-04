package stasis.test.specs.unit.client.ops.backup.stages

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import stasis.client.model.DatasetMetadata
import stasis.client.ops.backup.stages.MetadataCollection
import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures

class MetadataCollectionSpec extends AsyncUnitSpec {
  private implicit val system: ActorSystem = ActorSystem(name = "MetadataCollectionSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  "A Backup MetadataCollection stage" should "collect dataset metadata" in {
    val stage = new MetadataCollection {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
    }

    val stageInput = List(
      Right(Fixtures.Metadata.FileOneMetadata), // metadata changed
      Left(Fixtures.Metadata.FileTwoMetadata), // content changed
      Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
    )

    Source(stageInput)
      .via(stage.metadataCollection)
      .runFold(Seq.empty[DatasetMetadata])(_ :+ _)
      .map {
        case metadata :: Nil =>
          metadata should be(
            DatasetMetadata(
              contentChanged = Seq(Fixtures.Metadata.FileTwoMetadata),
              metadataChanged = Seq(Fixtures.Metadata.FileOneMetadata, Fixtures.Metadata.FileThreeMetadata)
            )
          )

        case metadata =>
          fail(s"Unexpected number of entries received: [${metadata.size}]")
      }
  }
}
