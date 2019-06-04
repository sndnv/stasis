package stasis.test.specs.unit.client.ops.recovery.stages

import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.{Files, LinkOption}
import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import stasis.client.analysis.Metadata
import stasis.client.model.FileMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.stages.MetadataApplication
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.ExecutionContext

class MetadataApplicationSpec extends AsyncUnitSpec { spec =>
  private implicit val system: ActorSystem = ActorSystem(name = "MetadataApplicationSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  "A Recovery MetadataApplication stage" should "apply metadata to files" in {
    val targetFile = Files.createTempFile("metadata-target-file", "")
    targetFile.toFile.deleteOnExit()

    val attributes = Files.readAttributes(targetFile, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

    val metadata = FileMetadata(
      path = targetFile,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = attributes.owner().getName,
      group = attributes.group().getName,
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    val stage = new MetadataApplication {
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def ec: ExecutionContext = spec.mat.executionContext
    }

    for {
      metadataBeforeApplication <- Metadata
        .extractFileMetadata(
          file = targetFile,
          withChecksum = BigInt(1),
          withCrate = Crate.generateId()
        )
      _ <- Source
        .single(metadata)
        .via(stage.metadataApplication)
        .runWith(Sink.ignore)
      metadataAfterApplication <- Metadata
        .extractFileMetadata(
          file = targetFile,
          withChecksum = BigInt(1),
          withCrate = Crate.generateId()
        )
    } yield {
      metadataBeforeApplication.permissions should not be metadata.permissions
      metadataBeforeApplication.updated should not be metadata.updated

      metadataAfterApplication.owner should be(metadata.owner)
      metadataAfterApplication.group should be(metadata.group)
      metadataAfterApplication.permissions should be(metadata.permissions)
      metadataAfterApplication.updated should be(metadata.updated)
    }
  }
}
