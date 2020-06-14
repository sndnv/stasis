package stasis.test.specs.unit.client.ops.recovery.stages

import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.{Files, LinkOption, Paths}
import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.api.clients.Clients
import stasis.client.model.{EntityMetadata, TargetEntity}
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

    val metadata = EntityMetadata.File(
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
      crates = Map(Paths.get(s"${targetFile}_0") -> Crate.generateId())
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

      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    val target = TargetEntity(
      path = metadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = metadata,
      currentMetadata = None
    )

    for {
      metadataBeforeApplication <- Metadata.extractBaseEntityMetadata(entity = targetFile)
      _ <- Source.single(target).via(stage.metadataApplication).runWith(Sink.ignore)
      metadataAfterApplication <- Metadata.extractBaseEntityMetadata(entity = targetFile)
    } yield {
      metadataBeforeApplication.permissions should not be metadata.permissions
      metadataBeforeApplication.updated should not be metadata.updated

      metadataAfterApplication.owner should be(metadata.owner)
      metadataAfterApplication.group should be(metadata.group)
      metadataAfterApplication.permissions should be(metadata.permissions)
      metadataAfterApplication.updated should be(metadata.updated)

      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(1)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataApplicationSpec")
}
