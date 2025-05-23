package stasis.test.specs.unit.client.ops.scheduling

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.ExecutionException
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.rules.RuleSet
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup
import stasis.client.ops.recovery
import stasis.client.ops.scheduling.DefaultOperationExecutor
import stasis.client.tracking.state.BackupState
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultOperationExecutorSpec extends AsyncUnitSpec with ResourceHelpers with Eventually with BeforeAndAfterAll {
  "A DefaultOperationExecutor" should "start backups with rules" in withRetry {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(backupTracker = mockTracker)

    val _ = executor
      .startBackupWithRules(
        definition = DatasetDefinition.generateId()
      )
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntitySkipped) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start backups with files" in withRetry {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(backupTracker = mockTracker)

    val _ = executor
      .startBackupWithEntities(
        definition = DatasetDefinition.generateId(),
        entities = Seq.empty
      )
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntitySkipped) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "resume backups from state" in withRetry {
    val operation = Operation.generateId()

    val mockTracker = new MockBackupTracker() {
      override def state: Future[Map[Operation.Id, BackupState]] =
        Future.successful(
          Map(
            operation -> BackupState
              .start(operation = operation, definition = DatasetDefinition.generateId())
          )
        )
    }

    val executor = createExecutor(backupTracker = mockTracker)

    val _ = executor
      .resumeBackup(operation = operation)
      .await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.Started) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntitySkipped) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start recoveries with definitions" in withRetry {
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
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start recoveries with entries" in withRetry {
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
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
    }
  }

  it should "start expirations" in withRetry {
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

  it should "start validations" in withRetry {
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

  it should "start key rotations" in withRetry {
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

  it should "stop running operations" in withRetry {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(slowEncryption = true, backupTracker = mockTracker)

    val operation = executor.startBackupWithRules(definition = DatasetDefinition.generateId()).await
    val _ = executor.stop(operation).await

    eventually[Assertion] {
      mockTracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
    }
  }

  it should "fail to stop operations that are not running" in withRetry {
    val executor = createExecutor()

    val operation = Operation.generateId()

    executor
      .stop(operation)
      .map { result => result should be(empty) }
  }

  it should "handle operation failures" in withRetry {
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

  it should "provide a list of active and completed operations" in withRetry {
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

  it should "provide the configured backup rules" in withRetry {
    val executor = createExecutor()

    executor.rules
      .map { rules =>
        rules.definitions.toList match {
          case (None, defaultRules) :: Nil => defaultRules.size should be(8)
          case result                      => fail(s"Unexpected result received: [$result]")
        }
      }
  }

  it should "fail to start an operation if one of the same type is already running" in withRetry {
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

  it should "fail to resume a backup that is already completed" in withRetry {
    val operation = Operation.generateId()

    val mockTracker = new MockBackupTracker() {
      override def state: Future[Map[Operation.Id, BackupState]] =
        Future.successful(
          Map(
            operation -> BackupState
              .start(operation = operation, definition = DatasetDefinition.generateId())
              .backupCompleted()
          )
        )
    }

    val executor = createExecutor(backupTracker = mockTracker)

    executor
      .resumeBackup(operation = operation)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Cannot resume operation with ID [$operation]; operation already completed")
      }
  }

  it should "fail to resume a backup no state for it can be found" in withRetry {
    val operation = Operation.generateId()

    val mockTracker = new MockBackupTracker()

    val executor = createExecutor(backupTracker = mockTracker)

    executor
      .resumeBackup(operation = operation)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Cannot resume operation with ID [$operation]; no existing state was found")
      }
  }

  private def createExecutor(
    slowEncryption: Boolean = false,
    backupTracker: MockBackupTracker = new MockBackupTracker(),
    recoveryTracker: MockRecoveryTracker = new MockRecoveryTracker()
  ): DefaultOperationExecutor = {
    val checksum = Checksum.MD5
    val staging = new MockFileStaging()
    val compression = MockCompression()
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
      compression = compression,
      encryptor = encryption,
      decryptor = encryption,
      clients = clients,
      track = backupTracker,
      telemetry = MockClientTelemetryContext()
    )

    implicit val recoveryProviders: recovery.Providers = recovery.Providers(
      checksum = checksum,
      staging = staging,
      compression = compression,
      decryptor = encryption,
      clients = clients,
      track = recoveryTracker,
      telemetry = MockClientTelemetryContext()
    )

    new DefaultOperationExecutor(
      config = DefaultOperationExecutor.Config(
        backup = DefaultOperationExecutor.Config.Backup(
          rulesLoader = () =>
            RuleSet.fromFiles(
              files = Seq("/ops/scheduling/test.rules".asTestResource)
            )(typedSystem.executionContext),
          limits = backup.Backup.Limits(
            maxChunkSize = 8192,
            maxPartSize = 16384
          )
        )
      ),
      secret = Fixtures.Secrets.Default
    )
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DefaultOperationExecutorSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
