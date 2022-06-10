package stasis.test.specs.unit.client.ops.scheduling

import java.util.concurrent.atomic.AtomicBoolean
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.concurrent.Eventually
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}
import stasis.client.ops.exceptions.OperationExecutionFailure
import stasis.client.ops.scheduling.DefaultOperationExecutor
import stasis.client.ops.{backup, recovery, ParallelismConfig}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._
import scala.concurrent.{ExecutionException, Future}
import scala.util.control.NonFatal

class DefaultOperationExecutorSpec extends AsyncUnitSpec with ResourceHelpers with Eventually with BeforeAndAfterAll {
  "A DefaultOperationExecutor" should "start backups with rules" in {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(backupTracker = mockTracker)

    val _ = executor
      .startBackupWithRules(
        definition = DatasetDefinition.generateId()
      )
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start backups with files" in {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(backupTracker = mockTracker)

    val _ = executor
      .startBackupWithEntities(
        definition = DatasetDefinition.generateId(),
        entities = Seq.empty
      )
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start recoveries with definitions" in {
    val mockTracker = new MockRecoveryTracker()
    val executor = createExecutor(recoveryTracker = mockTracker)

    val _ = executor
      .startRecoveryWithDefinition(
        definition = DatasetDefinition.generateId(),
        until = None,
        query = None,
        destination = None
      )
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start recoveries with entries" in {
    val mockTracker = new MockRecoveryTracker()
    val executor = createExecutor(recoveryTracker = mockTracker)

    val _ = executor
      .startRecoveryWithEntry(
        entry = DatasetEntry.generateId(),
        query = None,
        destination = None
      )
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start expirations" in {
    val executor = createExecutor()

    executor
      .startExpiration()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ExecutionException) =>
        e.getCause.getMessage should be("Expiration is not supported")
      }
  }

  it should "start validations" in {
    val executor = createExecutor()

    executor
      .startValidation()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ExecutionException) =>
        e.getCause.getMessage should be("Validation is not supported")
      }
  }

  it should "start key rotations" in {
    val executor = createExecutor()

    executor
      .startKeyRotation()
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: ExecutionException) =>
        e.getCause.getMessage should be("Key rotation is not supported")
      }
  }

  it should "stop running operations" in {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(slowEncryption = true, backupTracker = mockTracker)

    val operation = executor.startBackupWithRules(definition = DatasetDefinition.generateId()).await
    val _ = executor.stop(operation).await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "fail to stop operations that are not running" in {
    val executor = createExecutor()

    val operation = Operation.generateId()

    executor
      .stop(operation)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: OperationExecutionFailure) =>
        e.getMessage should be(s"Failed to stop [$operation]; operation not found")
      }
  }

  it should "handle operation failures" in {
    val executor = createExecutor()
    val failed = new AtomicBoolean(false)

    val runnableOperation = new executor.RunnableOperation(
      operation = new Operation {
        override def id: Operation.Id = Operation.generateId()
        override def start(): Future[Done] = {
          failed.set(true)
          Future.failed(new RuntimeException("test failure"))
        }
        override def stop(): Unit = ()
        override def `type`: Operation.Type = Operation.Type.Backup
      }
    )

    runnableOperation
      .runWithResult()
      .map { _ =>
        failed.get should be(true)
      }
  }

  it should "provide a list of active and completed operations" in {
    val executor = createExecutor()

    executor.active.await should be(empty)
    executor.completed.await should be(empty)

    eventually[Assertion] {
      val backup = executor.startBackupWithRules(definition = DatasetDefinition.generateId()).await
      executor.active.await.get(backup) should be(Some(Operation.Type.Backup))
    }

    eventually[Assertion] {
      executor.active.await should be(empty)
      executor.completed.await.values.toSeq.distinct should be(Seq(Operation.Type.Backup))
    }
  }

  it should "provide the configured backup rules" in {
    val executor = createExecutor()

    executor.rules
      .map { spec =>
        // test rules are not expected to match anything on the default filesystem
        spec.entries should be(empty)
        spec.unmatched should not be empty
      }
  }

  it should "fail to start an operation if one of the same type is already running" in {
    val executor = createExecutor(slowEncryption = true)

    eventually[Future[Assertion]] {
      executor.active.await should be(empty)

      eventually[Assertion] {
        val backup = executor.startBackupWithRules(definition = DatasetDefinition.generateId()).await
        executor.active.await.get(backup) should be(Some(Operation.Type.Backup))
      }

      executor
        .startBackupWithRules(definition = DatasetDefinition.generateId())
        .map { result =>
          fail(s"Unexpected result received: [$result]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should startWith("Cannot start [Backup] operation")
          e.getMessage should include("already active")
        }
    }
  }

  private def createExecutor(
    slowEncryption: Boolean = false,
    backupTracker: MockBackupTracker = new MockBackupTracker(),
    recoveryTracker: MockRecoveryTracker = new MockRecoveryTracker()
  ): DefaultOperationExecutor = {
    val checksum = Checksum.MD5
    val staging = new MockFileStaging()
    val compression = new MockCompression()
    val encryption = new MockEncryption() {
      override def encrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
        if (slowEncryption) {
          super.encrypt(fileSecret).throttle(elements = 1, per = 10.seconds)
        } else {
          super.encrypt(fileSecret)
        }

      override def encrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
        if (slowEncryption) {
          super.encrypt(metadataSecret).throttle(elements = 1, per = 10.seconds)
        } else {
          super.encrypt(metadataSecret)
        }

      override def decrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
        if (slowEncryption) {
          super.decrypt(fileSecret).throttle(elements = 1, per = 10.seconds)
        } else {
          super.decrypt(fileSecret)
        }

      override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
        if (slowEncryption) {
          super.decrypt(metadataSecret).throttle(elements = 1, per = 10.seconds)
        } else {
          super.decrypt(metadataSecret)
        }
    }
    val clients = Clients(
      api = MockServerApiEndpointClient(),
      core = MockServerCoreEndpointClient()
    )

    implicit val backupProviders: backup.Providers = backup.Providers(
      checksum = checksum,
      staging = staging,
      compressor = compression,
      encryptor = encryption,
      decryptor = encryption,
      clients = clients,
      track = backupTracker,
      telemetry = MockClientTelemetryContext()
    )

    implicit val recoveryProviders: recovery.Providers = recovery.Providers(
      checksum = checksum,
      staging = staging,
      decompressor = compression,
      decryptor = encryption,
      clients = clients,
      track = recoveryTracker,
      telemetry = MockClientTelemetryContext()
    )

    new DefaultOperationExecutor(
      config = DefaultOperationExecutor.Config(
        backup = DefaultOperationExecutor.Config.Backup(
          rulesFile = "/ops/scheduling/test.rules".asTestResource,
          limits = backup.Backup.Limits(
            maxChunkSize = 8192,
            maxPartSize = 16384
          )
        )
      ),
      secret = Fixtures.Secrets.Default
    )
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultOperationExecutorSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
