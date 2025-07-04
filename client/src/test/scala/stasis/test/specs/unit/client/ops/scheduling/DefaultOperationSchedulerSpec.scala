package stasis.test.specs.unit.client.ops.scheduling

import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

import stasis.client.api.clients.Clients
import stasis.client.ops.exceptions.ScheduleRetrievalFailure
import stasis.client.ops.scheduling.DefaultOperationScheduler
import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockOperationExecutor
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultOperationSchedulerSpec extends AsyncUnitSpec with ResourceHelpers with Eventually with BeforeAndAfterAll {
  "A DefaultOperationScheduler" should "support loading schedule assignments from a file" in {
    val schedule1 = UUID.fromString("c7a1a7c4-94e1-44a8-8365-9fc44c8f51ea")
    val schedule2 = UUID.fromString("b859d9fb-1962-4a29-b18d-2c7c835cf18b")
    val schedule3 = UUID.fromString("4bb8ab15-e8ba-40dd-b814-b8b112a79147")
    val schedule4 = UUID.fromString("ce849f2c-e170-427c-8e9d-0e3d8cdcf700")
    val schedule5 = UUID.fromString("2f059ab6-3db2-4a34-8082-780d673f6519")

    val definition1 = UUID.fromString("ea371971-adc5-44ba-9d93-6f65507d6967")
    val definition2 = UUID.fromString("a1a55d45-b3b2-48df-b628-2fb2e7e022a4")

    val expectedSchedules = Seq(
      OperationScheduleAssignment.Backup(
        schedule = schedule1,
        definition = definition1,
        entities = Seq.empty
      ),
      OperationScheduleAssignment.Backup(
        schedule = schedule2,
        definition = definition2,
        entities = Seq(Paths.get("/work/file-01"), Paths.get("/work/file-02"))
      ),
      OperationScheduleAssignment.Expiration(schedule = schedule3),
      OperationScheduleAssignment.Validation(schedule = schedule4),
      OperationScheduleAssignment.KeyRotation(schedule = schedule5)
    )

    DefaultOperationScheduler
      .loadSchedules(file = "/ops/scheduling/test.schedules".asTestResource)
      .map { actualSchedules =>
        actualSchedules should be(expectedSchedules)
      }
  }

  it should "provide a list of active schedules" in {
    val scheduler = createScheduler()

    managedScheduler(scheduler) {
      eventually[Assertion] {
        val schedules = scheduler.schedules.await

        schedules.toList match {
          case backup1 :: backup2 :: expiration :: validation :: keyRotation :: Nil =>
            backup1.assignment shouldBe an[OperationScheduleAssignment.Backup]
            backup1.schedule shouldBe a[Right[_, Schedule]]

            backup2.assignment shouldBe an[OperationScheduleAssignment.Backup]
            backup2.schedule shouldBe a[Right[_, Schedule]]

            expiration.assignment shouldBe an[OperationScheduleAssignment.Expiration]
            expiration.schedule shouldBe a[Right[_, Schedule]]

            validation.assignment shouldBe an[OperationScheduleAssignment.Validation]
            validation.schedule shouldBe a[Right[_, Schedule]]

            keyRotation.assignment shouldBe an[OperationScheduleAssignment.KeyRotation]
            keyRotation.schedule shouldBe a[Right[_, Schedule]]

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "support refreshing the list of active schedules" in {
    val mockApiClient = MockServerApiEndpointClient()
    val scheduler = createScheduler(api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(5)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      }

      val _ = scheduler.refresh().await

      eventually[Assertion] {
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(10)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
      }
    }
  }

  it should "support stopping itself" in {
    val scheduler = createScheduler()

    eventually[Assertion] {
      scheduler.schedules.await should not be empty
    }

    val _ = scheduler.stop().await

    scheduler.schedules
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should (startWith("Ask timed out") or include("had already been terminated"))
      }
  }

  it should "handle failures during active schedule retrieval" in {
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] =
        Future.failed(new RuntimeException("test failure"))
    }

    val scheduler = createScheduler(api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        val schedules = scheduler.schedules.await

        schedules.toList match {
          case backup1 :: backup2 :: expiration :: validation :: keyRotation :: Nil =>
            backup1.assignment shouldBe an[OperationScheduleAssignment.Backup]
            backup1.schedule shouldBe a[Left[ScheduleRetrievalFailure, _]]

            backup2.assignment shouldBe an[OperationScheduleAssignment.Backup]
            backup2.schedule shouldBe a[Left[ScheduleRetrievalFailure, _]]

            expiration.assignment shouldBe an[OperationScheduleAssignment.Expiration]
            expiration.schedule shouldBe a[Left[ScheduleRetrievalFailure, _]]

            validation.assignment shouldBe an[OperationScheduleAssignment.Validation]
            validation.schedule shouldBe a[Left[ScheduleRetrievalFailure, _]]

            keyRotation.assignment shouldBe an[OperationScheduleAssignment.KeyRotation]
            keyRotation.schedule shouldBe a[Left[ScheduleRetrievalFailure, _]]

          case other =>
            fail(s"Unexpected result received: [$other]")
        }
      }
    }
  }

  it should "support scheduling operation execution" in {
    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) defaultMinDelay / 2 else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be >= 1
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "not schedule operation execution when no active schedules are available" in {
    val mockExecutor = MockOperationExecutor()

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] =
        Future.failed(new RuntimeException("test failure"))
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        await(delay = defaultMinDelay * 2)

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "support a minimum scheduling delay" in {
    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) 1.millisecond else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient, maxAdditionalDelay = 1.millis)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty
      }

      await(delay = defaultMinDelay / 2)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)

      await(delay = defaultMinDelay)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  it should "support additional random scheduling delay" in {
    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) 1.millisecond else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val maxDelay = 50.millis

    val scheduler = createScheduler(
      executor = mockExecutor,
      api = mockApiClient,
      minDelay = 1.millis,
      maxAdditionalDelay = maxDelay
    )

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty
      }

      await(delay = maxDelay * 2 + maxDelay / 2)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be >= 2
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  it should "not execute an operation if it is already active" in {
    val mockExecutor = new MockOperationExecutor() {
      override def startBackupWithRules(definition: DatasetDefinition.Id): Future[Operation.Id] =
        after(delay = 1.second)(super.startBackupWithRules(definition))
    }

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) 1.millisecond else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(
      executor = mockExecutor,
      api = mockApiClient,
      minDelay = 1.millisecond,
      maxAdditionalDelay = 1.millisecond
    )

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(1)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }

      val _ = scheduler.refresh().await
      await(delay = defaultMinDelay)

      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(1)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "handle operation execution failures" in {
    val failed = new AtomicInteger(0)

    val mockExecutor = new MockOperationExecutor() {
      override def startBackupWithRules(definition: DatasetDefinition.Id): Future[Operation.Id] = {
        val _ = failed.incrementAndGet()
        Future.failed(new RuntimeException("test failure"))
      }
    }

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) 1.millisecond else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val maxDelay = 50.millis

    val scheduler = createScheduler(
      executor = mockExecutor,
      api = mockApiClient,
      minDelay = 1.millis,
      maxAdditionalDelay = maxDelay
    )

    managedScheduler(scheduler) {
      await(delay = maxDelay * 2)

      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)

        failed.get should be >= 2
      }
    }
  }

  it should "support executing backup operations (for definition)" in {
    val backupSchedule = java.util.UUID.fromString("c7a1a7c4-94e1-44a8-8365-9fc44c8f51ea")

    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) defaultMinDelay / 2 else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be >= 1
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "support executing backup operations (with files)" in {
    val backupSchedule = java.util.UUID.fromString("b859d9fb-1962-4a29-b18d-2c7c835cf18b")

    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == backupSchedule) defaultMinDelay / 2 else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be >= 1
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "support executing expiration operations" in {
    val expirationSchedule = java.util.UUID.fromString("4bb8ab15-e8ba-40dd-b814-b8b112a79147")

    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == expirationSchedule) defaultMinDelay / 2 else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be >= 1
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "support executing validation operations" in {
    val validationSchedule = java.util.UUID.fromString("ce849f2c-e170-427c-8e9d-0e3d8cdcf700")

    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == validationSchedule) defaultMinDelay / 2 else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be >= 1
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  it should "support executing key rotation operations" in {
    val keyRotationSchedule = java.util.UUID.fromString("2f059ab6-3db2-4a34-8082-780d673f6519")

    val mockExecutor = MockOperationExecutor()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
        val interval = if (schedule == keyRotationSchedule) defaultMinDelay / 2 else 1.hour
        super.publicSchedule(schedule).map(_.copy(interval = interval))(typedSystem.executionContext)
      }
    }

    val scheduler = createScheduler(executor = mockExecutor, api = mockApiClient)

    managedScheduler(scheduler) {
      eventually[Assertion] {
        scheduler.schedules.await should not be empty

        mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
        mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be >= 1
        mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private val defaultMinDelay: FiniteDuration = 100.millis
  private val defaultMaxAdditionalDelay: FiniteDuration = defaultMinDelay * 2
  private val backupSchedule: Schedule.Id = java.util.UUID.fromString("c7a1a7c4-94e1-44a8-8365-9fc44c8f51ea")

  private def createScheduler(
    executor: MockOperationExecutor = MockOperationExecutor(),
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    minDelay: FiniteDuration = defaultMinDelay,
    maxAdditionalDelay: FiniteDuration = defaultMaxAdditionalDelay
  ): DefaultOperationScheduler =
    DefaultOperationScheduler(
      config = DefaultOperationScheduler.Config(
        schedulesFile = "/ops/scheduling/test.schedules".asTestResource,
        minDelay = minDelay,
        maxExtraDelay = maxAdditionalDelay
      ),
      clients = Clients(api = api, core = null),
      executor = executor
    )

  private def managedScheduler(scheduler: DefaultOperationScheduler)(block: => Assertion): Assertion =
    try {
      block
    } finally {
      val _ = scheduler.stop().await
    }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DefaultOperationSchedulerSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
