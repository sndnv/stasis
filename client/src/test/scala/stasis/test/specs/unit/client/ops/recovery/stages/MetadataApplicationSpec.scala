package stasis.test.specs.unit.client.ops.recovery.stages

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.PosixFileAttributes
import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.api.clients.Clients
import stasis.client.model.FileMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.MetadataApplication
import stasis.core.packaging.Crate
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.concurrent.ExecutionContext

class MetadataApplicationSpec extends AsyncUnitSpec { spec =>
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

    val mockTracker = new MockRecoveryTracker

    val stage = new MetadataApplication {
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)

      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = new MockFileStaging,
        decompressor = new MockCompression,
        decryptor = new MockEncryption,
        clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
        track = mockTracker
      )

      override implicit protected def ec: ExecutionContext = spec.mat.executionContext
    }

    implicit val operationId: Operation.Id = Operation.generateId()

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

      mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(1)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataApplicationSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
