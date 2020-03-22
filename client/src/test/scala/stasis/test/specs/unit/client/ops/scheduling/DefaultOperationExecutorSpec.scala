package stasis.test.specs.unit.client.ops.scheduling

import java.util.concurrent.atomic.AtomicBoolean

import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.scalatest.concurrent.Eventually
import org.scalatest.BeforeAndAfterAll
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

import scala.concurrent.{ExecutionException, Future}
import scala.concurrent.duration._
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

    eventually {
      mockTracker.statistics(MockBackupTracker.Statistic.FileExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.FileCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.FileProcessed) should be(0)
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
      .startBackupWithFiles(
        definition = DatasetDefinition.generateId(),
        files = Seq.empty
      )
      .await

    eventually {
      mockTracker.statistics(MockBackupTracker.Statistic.FileExamined) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.FileCollected) should be(0)
      mockTracker.statistics(MockBackupTracker.Statistic.FileProcessed) should be(0)
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
        query = None
      )
      .await

    eventually {
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
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
        query = None
      )
      .await

    eventually {
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
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
      .recover {
        case NonFatal(e: ExecutionException) =>
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
      .recover {
        case NonFatal(e: ExecutionException) =>
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
      .recover {
        case NonFatal(e: ExecutionException) =>
          e.getCause.getMessage should be("Key rotation is not supported")
      }
  }

  it should "stop running operations" in {
    val mockTracker = new MockBackupTracker()
    val executor = createExecutor(slowEncryption = true, backupTracker = mockTracker)

    val operation = executor.startBackupWithRules(definition = DatasetDefinition.generateId()).await
    val _ = executor.stop(operation).await

    eventually {
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
      .recover {
        case NonFatal(e: OperationExecutionFailure) =>
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

  it should "provide a list of active operations" in {
    val executor = createExecutor(slowEncryption = true)

    executor.operations.await should be(empty)

    eventually {
      val backup = executor.startBackupWithRules(definition = DatasetDefinition.generateId()).await
      executor.operations.await.get(backup) should be(Some(Operation.Type.Backup))
    }

    eventually {
      executor.operations.await should be(empty)
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
      track = backupTracker
    )

    implicit val recoveryProviders: recovery.Providers = recovery.Providers(
      checksum = checksum,
      staging = staging,
      decompressor = compression,
      decryptor = encryption,
      clients = clients,
      track = recoveryTracker
    )

    new DefaultOperationExecutor(
      config = DefaultOperationExecutor.Config(
        rulesFile = "/ops/scheduling/test.rules".asTestResource
      ),
      secret = Fixtures.Secrets.Default
    )
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "DefaultOperationExecutorSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toUntyped

  private implicit val mat: Materializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
