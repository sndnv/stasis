package stasis.test.specs.unit.client.ops.backup.stages.internal

import java.nio.file.{Path, Paths}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.internal.StagedSubFlow
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class StagedSubFlowSpec extends AsyncUnitSpec with Eventually {
  "A StagedSubFlow" should "support dynamic creation of staging flows" in {
    val mockStaging = new MockFileStaging()
    val mockEncryption = new MockEncryption()

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = mockStaging,
      compressor = new MockCompression(),
      encryptor = mockEncryption,
      decryptor = mockEncryption,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockBackupTracker
    )

    val expectedPartPath = Paths.get("/tmp/file/one_0")

    for {
      flow <- StagedSubFlow.createStagingFlow(
        partSecret = DeviceFileSecret(
          file = expectedPartPath,
          iv = ByteString.empty,
          key = ByteString.empty
        )
      )
      (actualPartPath, stagedPath) <- Source.single(ByteString("test")).via(flow).runWith(Sink.head)
    } yield {
      actualPartPath should be(expectedPartPath)
      stagedPath should be(mockStaging.temporaryPath)

      eventually[Assertion] {
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
        mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

        mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(1)
        mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)
      }
    }
  }

  it should "handle staging failures" in {
    val mockStaging = new MockFileStaging()

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = mockStaging,
      compressor = new MockCompression(),
      encryptor = new MockEncryption(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockBackupTracker
    )

    val stagedParts = new java.util.LinkedList[(Path, Path)](
      (
        (Paths.get("/tmp/file/one_0"), Paths.get("/tmp/file/staged_1"))
          :: (Paths.get("/tmp/file/two_0"), Paths.get("/tmp/file/staged_2"))
          :: (Paths.get("/tmp/file/two_1"), Paths.get("/tmp/file/staged_3"))
          :: Nil
      ).asJava
    )

    val failure = new RuntimeException("test failure")
    StagedSubFlow
      .handleStagingFailure[Done](stagedParts)
      .apply(failure)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recoverWith {
        case NonFatal(e) =>
          e.getMessage should be(failure.getMessage)

          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(3)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)
      }
  }

  it should "handle discarding failures during staging failures" in {
    val failure = new RuntimeException("test failure")

    val mockStaging = new MockFileStaging() {
      override def discard(file: Path): Future[Done] = Future.failed(failure)
    }

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = mockStaging,
      compressor = new MockCompression(),
      encryptor = new MockEncryption(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockBackupTracker
    )

    val stagedParts = new java.util.LinkedList[(Path, Path)](
      ((Paths.get("/tmp/file/one_0"), Paths.get("/tmp/file/staged_1")) :: Nil).asJava
    )

    StagedSubFlow
      .handleStagingFailure[Done](stagedParts)
      .apply(failure)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recoverWith {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Encountered discarding failure [${failure.getMessage}] while processing staging failure: [${failure.getMessage}]"
          )

          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)
      }
  }

  it should "support staging a partitioned data stream" in {
    val mockStaging = new MockFileStaging()
    val mockEncryption = new MockEncryption()

    val original = Source("part1" :: "part2" :: Nil)
      .map(ByteString.apply)
      .mapMaterializedValue(_ => Future.successful(IOResult.createSuccessful(0)))
      .splitWhen(element => element == ByteString("part2"))

    val extended = new StagedSubFlow(subFlow = original)

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = mockStaging,
      compressor = new MockCompression(),
      encryptor = mockEncryption,
      decryptor = mockEncryption,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockBackupTracker
    )

    extended
      .stage(
        withPartSecret = partId =>
          DeviceFileSecret(
            file = Paths.get(s"/tmp/file/one_$partId"),
            iv = ByteString.empty,
            key = ByteString.empty
        )
      )
      .map { result =>
        result.toList match {
          case (part1Path, part1Staged) :: (part2Path, part2Staged) :: Nil =>
            part1Path.toString should be("/tmp/file/one_0")
            part1Staged should be(mockStaging.temporaryPath)

            part2Path.toString should be("/tmp/file/one_1")
            part2Staged should be(mockStaging.temporaryPath)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        eventually[Assertion] {
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(2)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(2)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)
        }
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "StagedSubFlowSpec")

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
