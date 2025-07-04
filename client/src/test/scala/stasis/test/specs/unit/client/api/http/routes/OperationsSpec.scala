package stasis.test.specs.unit.client.api.http.routes

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.slf4j.LoggerFactory

import stasis.client.api.Context
import stasis.client.api.http.routes.Operations
import stasis.client.api.http.routes.Operations.SpecificationRules
import stasis.client.collection.rules.Rule
import stasis.client.ops.recovery.Recovery.PathQuery
import stasis.client.tracking.BackupTracker
import stasis.client.tracking.RecoveryTracker
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.RecoveryState
import io.github.sndnv.layers.telemetry.mocks.MockAnalyticsCollector
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.api.http.routes.OperationsSpec.PartialBackupState
import stasis.test.specs.unit.client.api.http.routes.OperationsSpec.PartialRecoveryState
import stasis.test.specs.unit.client.mocks._

class OperationsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  "Operations routes" should "provide current operations state (default / active)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import Operations._

    val operations: Map[Operation.Id, Operation.Type] = Map(
      Operation.generateId() -> Operation.Type.Backup,
      Operation.generateId() -> Operation.Type.Recovery,
      Operation.generateId() -> Operation.Type.Backup
    )

    val mockTrackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker {
        override def state: Future[Map[Operation.Id, BackupState]] =
          super.state.map { _ =>
            operations.collect { case (k, Operation.Type.Backup) =>
              k -> BackupState.start(k, DatasetDefinition.generateId()).backupCompleted()
            }
          }
      }
      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker {
        override def state: Future[Map[Operation.Id, RecoveryState]] =
          super.state.map { _ =>
            operations.collect { case (k, Operation.Type.Recovery) => k -> RecoveryState.start(k) }
          }
      }
    }

    val mockExecutor = new MockOperationExecutor() {
      override def active: Future[Map[Operation.Id, Operation.Type]] =
        super.active.map { _ => operations.filter(_._2 == Operation.Type.Recovery) }
    }

    val routes = createRoutes(executor = mockExecutor, trackers = mockTrackers)

    Get("/") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[OperationProgress]].size should be(1)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
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

  they should "provide current operations state (completed)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import Operations._

    val operations: Map[Operation.Id, Operation.Type] = Map(
      Operation.generateId() -> Operation.Type.Backup,
      Operation.generateId() -> Operation.Type.Recovery,
      Operation.generateId() -> Operation.Type.Backup
    )

    val mockTrackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker {
        override def state: Future[Map[Operation.Id, BackupState]] =
          super.state.map { _ =>
            operations.collect { case (k, Operation.Type.Backup) =>
              k -> BackupState.start(k, DatasetDefinition.generateId()).backupCompleted()
            }
          }
      }
      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker {
        override def state: Future[Map[Operation.Id, RecoveryState]] =
          super.state.map { _ =>
            operations.collect { case (k, Operation.Type.Recovery) => k -> RecoveryState.start(k) }
          }
      }
    }

    val mockExecutor = MockOperationExecutor()

    val routes = createRoutes(executor = mockExecutor, trackers = mockTrackers)

    Get("/?state=completed") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[OperationProgress]].size should be(2)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
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

  they should "provide current operations state (all)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import Operations._

    val operations: Map[Operation.Id, Operation.Type] = Map(
      Operation.generateId() -> Operation.Type.Backup,
      Operation.generateId() -> Operation.Type.Recovery,
      Operation.generateId() -> Operation.Type.Backup
    )

    val mockTrackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker {
        override def state: Future[Map[Operation.Id, BackupState]] =
          super.state.map { _ =>
            operations.collect { case (k, Operation.Type.Backup) =>
              k -> BackupState.start(k, DatasetDefinition.generateId()).backupCompleted()
            }
          }
      }
      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker {
        override def state: Future[Map[Operation.Id, RecoveryState]] =
          super.state.map { _ =>
            operations.collect { case (k, Operation.Type.Recovery) => k -> RecoveryState.start(k) }
          }
      }
    }

    val mockExecutor = MockOperationExecutor()

    val routes = createRoutes(executor = mockExecutor, trackers = mockTrackers)

    Get("/?state=all") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[OperationProgress]].size should be(3)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
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

  they should "provide all current backup rules" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.client.api.http.Formats.ruleFormat

    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    Get("/backup/rules") ~> routes ~> check {
      status should be(StatusCodes.OK)

      responseAs[Map[String, Seq[Rule]]].toList match {
        case ("default", defaultRules) :: (_, otherRules) :: Nil =>
          defaultRules should be(
            Seq(
              Rule(Rule.Operation.Include, "/tmp/file", "*", None, Rule.Original("+ /tmp/file *", 0)),
              Rule(Rule.Operation.Include, "/tmp/other", "*", None, Rule.Original("+ /tmp/other *", 1))
            )
          )

          otherRules should be(
            Seq(
              Rule(Rule.Operation.Exclude, "/tmp/other/def1", "*", None, Rule.Original("- /tmp/other/def1 *", 0))
            )
          )

        case result =>
          fail(s"Unexpected result received: [$result]")
      }

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(1)
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

  they should "provide current default backup rules" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.client.api.http.Formats.ruleFormat

    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    Get(s"/backup/rules/default") ~> routes ~> check {
      status should be(StatusCodes.OK)

      val rules = responseAs[Seq[Rule]]
      rules should be(
        Seq(
          Rule(Rule.Operation.Include, "/tmp/file", "*", None, Rule.Original("+ /tmp/file *", 0)),
          Rule(Rule.Operation.Include, "/tmp/other", "*", None, Rule.Original("+ /tmp/other *", 1))
        )
      )

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(1)
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

  they should "provide current backup rules for a specific definition" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.client.api.http.Formats.ruleFormat

    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition1 = MockOperationExecutor.definitionWithRules
    val definition2 = DatasetDefinition.generateId()

    Get(s"/backup/rules/$definition1") ~> routes ~> check {
      status should be(StatusCodes.OK)

      val rules = responseAs[Seq[Rule]]
      rules should be(
        Seq(
          Rule(Rule.Operation.Exclude, "/tmp/other/def1", "*", None, Rule.Original("- /tmp/other/def1 *", 0))
        )
      )
    }

    Get(s"/backup/rules/$definition2") ~> routes ~> check {
      status should be(StatusCodes.OK)

      val rules = responseAs[Seq[Rule]] // default rules

      rules should be(
        Seq(
          Rule(Rule.Operation.Include, "/tmp/file", "*", None, Rule.Original("+ /tmp/file *", 0)),
          Rule(Rule.Operation.Include, "/tmp/other", "*", None, Rule.Original("+ /tmp/other *", 1))
        )
      )
    }

    mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
    mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
    mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(2)
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

  they should "provide current default backup specification" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    Get(s"/backup/rules/default/specification") ~> routes ~> check {
      status should be(StatusCodes.OK)

      val spec = responseAs[SpecificationRules]
      spec.included shouldBe empty
      spec.excluded shouldBe empty
      spec.unmatched should be(
        Seq(
          Rule.Original("+ /tmp/file *", 0) -> "NoSuchFileException: /tmp/file",
          Rule.Original("+ /tmp/other *", 1) -> "NoSuchFileException: /tmp/other"
        )
      )
    }

    mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
    mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
    mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(1)
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

  they should "provide current backup specification for a definition" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = MockOperationExecutor.definitionWithRules

    Get(s"/backup/rules/$definition/specification") ~> routes ~> check {
      status should be(StatusCodes.OK)

      val spec = responseAs[SpecificationRules]
      spec.included shouldBe empty
      spec.excluded shouldBe empty
      spec.unmatched should be(
        Seq(
          Rule.Original("- /tmp/other/def1 *", 0) -> "NoSuchFileException: /tmp/other/def1"
        )
      )
    }

    mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
    mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
    mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(1)
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

  they should "support starting backups" in withRetry {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()

    Put(s"/backup/$definition") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
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

  they should "support starting recoveries with the latest entry for a definition" in withRetry {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()

    Put(s"/recover/$definition/latest") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support starting recoveries with the latest entry until a timestamp" in withRetry {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()

    Put(s"/recover/$definition/until/${Instant.now()}") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support starting recoveries with a specific entry" in withRetry {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()
    val entry = DatasetEntry.generateId()

    Put(s"/recover/$definition/from/$entry") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support retrieving progress of specific operations (backup)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import OperationsSpec._

    val operation = Operation.generateId()

    val mockTrackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker() {
        override def state: Future[Map[Operation.Id, BackupState]] =
          Future.successful(
            Map(
              operation -> BackupState.start(operation = operation, definition = DatasetDefinition.generateId())
            )
          )
      }
    }

    val routes = createRoutes(executor = MockOperationExecutor(), trackers = mockTrackers)

    Get(s"/$operation/progress") ~> routes ~> check {
      status should be(StatusCodes.OK)

      responseAs[PartialBackupState] should be(
        PartialBackupState(
          operation = operation,
          entities = PartialBackupState.Entities(
            discovered = Seq.empty,
            unmatched = Seq.empty,
            examined = Seq.empty,
            collected = Seq.empty,
            pending = Map.empty,
            processed = Map.empty,
            failed = Map.empty
          ),
          metadataCollected = None,
          metadataPushed = None,
          failures = Seq.empty,
          completed = None
        )
      )
    }
  }

  they should "support retrieving progress of specific operations (recovery)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import OperationsSpec._

    val operation = Operation.generateId()
    val mockTrackers = new MockTrackerViews() {
      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker() {
        override def state: Future[Map[Operation.Id, RecoveryState]] =
          Future.successful(
            Map(
              operation -> RecoveryState.start(operation = operation)
            )
          )
      }
    }

    val routes = createRoutes(executor = MockOperationExecutor(), trackers = mockTrackers)

    Get(s"/$operation/progress") ~> routes ~> check {
      status should be(StatusCodes.OK)

      responseAs[PartialRecoveryState] should be(
        PartialRecoveryState(
          operation = operation,
          entities = PartialRecoveryState.Entities(
            examined = Seq.empty,
            collected = Seq.empty,
            pending = Map.empty,
            processed = Map.empty,
            metadataApplied = Seq.empty,
            failed = Map.empty
          ),
          failures = Seq.empty,
          completed = None
        )
      )
    }
  }

  they should "fail to retrieve progress of a specific operation if it does not exist" in withRetry {
    val operation = Operation.generateId()

    val routes = createRoutes()

    Get(s"/$operation/progress") ~> routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "support retrieving progress stream for specific operations (backup)" in withRetry {
    import org.apache.pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
    import play.api.libs.json.Json

    val operation = Operation.generateId()

    val backup = BackupState.start(operation = operation, definition = DatasetDefinition.generateId())

    val entity1 = Paths.get("/tmp/a")

    val events = List(
      backup,
      backup.entityExamined(entity1),
      backup.entityExamined(entity1).entityFailed(entity1, new RuntimeException("Test failure"))
    )

    val heartbeatInterval = 50.millis

    val mockTrackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker() {
        override def exists(operation: Operation.Id): Future[Boolean] =
          Future.successful(true)

        override def updates(operation: Operation.Id): Source[BackupState, NotUsed] =
          Source(events).throttle(elements = 1, per = heartbeatInterval * 4)
      }
    }

    val mockExecutor = new MockOperationExecutor() {
      override def active: Future[Map[Operation.Id, Operation.Type]] =
        Future.successful(Map(operation -> Operation.Type.Backup))
    }

    val routes = createRoutes(executor = mockExecutor, trackers = mockTrackers)

    Get(s"/$operation/follow?heartbeat=${heartbeatInterval.toMillis}ms") ~> routes ~> check {
      status should be(StatusCodes.OK)
      mediaType should be(MediaTypes.`text/event-stream`)

      val actualEvents =
        Unmarshal(response)
          .to[Source[ServerSentEvent, NotUsed]]
          .flatMap(_.map(event => Json.parse(event.data).as[PartialBackupState]).runWith(Sink.seq))
          .await

      actualEvents.toList match {
        case first :: second :: third :: Nil =>
          first.operation should be(backup.operation)
          first.entities.examined should be(empty)
          first.entities.failed should be(empty)

          second.operation should be(backup.operation)
          second.entities.examined should be(Seq("/tmp/a"))
          second.entities.failed should be(empty)

          third.operation should be(backup.operation)
          third.entities.examined should be(Seq("/tmp/a"))
          third.entities.failed should be(Map("/tmp/a" -> "RuntimeException - Test failure"))

        case other =>
          fail(s"Unexpected events encountered: [$other]")
      }
    }
  }

  they should "support retrieving progress stream for specific operations (recovery)" in withRetry {
    import org.apache.pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
    import play.api.libs.json.Json

    val operation = Operation.generateId()

    val recovery = RecoveryState.start(operation = operation)

    val entity1 = Paths.get("/tmp/a")

    val events = List(
      recovery,
      recovery.entityExamined(entity1),
      recovery.entityExamined(entity1).entityFailed(entity1, new RuntimeException("Test failure"))
    )

    val heartbeatInterval = 50.millis

    val mockTrackers = new MockTrackerViews() {
      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker() {
        override def exists(operation: Operation.Id): Future[Boolean] =
          Future.successful(true)

        override def updates(operation: Operation.Id): Source[RecoveryState, NotUsed] =
          Source(events).throttle(elements = 1, per = heartbeatInterval * 4)
      }
    }

    val mockExecutor = new MockOperationExecutor() {
      override def active: Future[Map[Operation.Id, Operation.Type]] =
        Future.successful(Map(operation -> Operation.Type.Recovery))
    }

    val routes = createRoutes(executor = mockExecutor, trackers = mockTrackers)

    Get(s"/$operation/follow?heartbeat=${heartbeatInterval.toMillis}ms") ~> routes ~> check {
      status should be(StatusCodes.OK)
      mediaType should be(MediaTypes.`text/event-stream`)

      val actualEvents =
        Unmarshal(response)
          .to[Source[ServerSentEvent, NotUsed]]
          .flatMap(_.map(event => Json.parse(event.data).as[PartialRecoveryState]).runWith(Sink.seq))
          .await

      actualEvents.toList match {
        case first :: second :: third :: Nil =>
          first.operation should be(recovery.operation)
          first.entities.examined should be(empty)
          first.entities.failed should be(empty)

          second.operation should be(recovery.operation)
          second.entities.examined should be(Seq("/tmp/a"))
          second.entities.failed should be(empty)

          third.operation should be(recovery.operation)
          third.entities.examined should be(Seq("/tmp/a"))
          third.entities.failed should be(Map("/tmp/a" -> "RuntimeException - Test failure"))

        case other =>
          fail(s"Unexpected events encountered: [$other]")
      }
    }
  }

  they should "fail to retrieve a progress stream for a specific operation if it does not exist" in withRetry {
    val operation = Operation.generateId()

    val routes = createRoutes()

    Get(s"/$operation/follow") ~> routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "support stopping running operations" in withRetry {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val operation = Operation.generateId()

    Put(s"/$operation/stop") ~> routes ~> check {
      status should be(StatusCodes.NoContent)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(1)
    }
  }

  they should "fail to stop missing operations" in withRetry {
    val mockExecutor = new MockOperationExecutor() {
      override def stop(operation: Operation.Id): Future[Option[Done]] = Future.successful(None)
    }
    val routes = createRoutes(executor = mockExecutor)

    val operation = Operation.generateId()

    Put(s"/$operation/stop") ~> routes ~> check {
      status should be(StatusCodes.NotFound)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
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

  they should "support resuming backup operations" in withRetry {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val operation = Operation.generateId()

    Put(s"/$operation/resume") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetActiveOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetCompletedOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.GetRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.ResumeBackupWithState) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support removing operations" in withRetry {
    val backupRemoved = new AtomicBoolean(false)
    val recoveryRemoved = new AtomicBoolean(false)

    val trackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker() {
        override def remove(operation: Operation.Id): Unit = backupRemoved.set(true)
      }

      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker() {
        override def remove(operation: Operation.Id): Unit = recoveryRemoved.set(true)
      }
    }

    val routes = createRoutes(trackers = trackers)

    val operation = Operation.generateId()

    Delete(s"/$operation") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      backupRemoved.get() should be(true)
      recoveryRemoved.get() should be(true)
    }
  }

  they should "fail to remove active operations" in withRetry {
    val operation = Operation.generateId()

    val mockExecutor = new MockOperationExecutor() {
      override def active: Future[Map[Operation.Id, Operation.Type]] = Future.successful(
        Map(
          operation -> Operation.Type.Backup
        )
      )
    }

    val backupRemoved = new AtomicBoolean(false)
    val recoveryRemoved = new AtomicBoolean(false)

    val trackers = new MockTrackerViews() {
      override val backup: BackupTracker.View with BackupTracker.Manage = new MockBackupTracker() {
        override def remove(operation: Operation.Id): Unit = backupRemoved.set(true)
      }

      override val recovery: RecoveryTracker.View with RecoveryTracker.Manage = new MockRecoveryTracker() {
        override def remove(operation: Operation.Id): Unit = recoveryRemoved.set(true)
      }
    }

    val routes = createRoutes(executor = mockExecutor, trackers = trackers)

    Delete(s"/$operation") ~> routes ~> check {
      status should be(StatusCodes.Conflict)

      backupRemoved.get() should be(false)
      recoveryRemoved.get() should be(false)
    }
  }

  they should "provide support for path query parameters" in withRetry {
    import stasis.client.api.http.routes.Operations._

    val route = Route.seal(
      pathEndOrSingleSlash {
        parameter("regex".as[PathQuery]) { _ =>
          Directives.complete(StatusCodes.OK)
        }
      }
    )

    (matchingFileNameRegexes ++ matchingPathRegexes).foreach { regex =>
      withClue(s"Parsing supported regex [$regex]:") {
        Get(Uri("/").withQuery(query = Uri.Query("regex" -> regex))) ~> route ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    succeed
  }

  they should "provide support for operation state parameters" in withRetry {
    import stasis.client.api.http.routes.Operations._

    val route = Route.seal(
      pathEndOrSingleSlash {
        parameter("state".as[State]) { _ =>
          Directives.complete(StatusCodes.OK)
        }
      }
    )

    val states = Seq("active", "completed", "all")

    states.foreach { state =>
      withClue(s"Parsing supported state [$state]:") {
        Get(Uri("/").withQuery(query = Uri.Query("state" -> state))) ~> route ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    succeed
  }

  they should "extract recovery destination parameters" in withRetry {
    import stasis.client.api.http.routes.Operations._

    val expectedPath = "/tmp/some/path"

    val route = Route.seal(
      pathEndOrSingleSlash {
        extractDestination(destinationParam = "dest", keepStructureParam = "keep") {
          case Some(destination) =>
            destination.path should be(expectedPath)
            destination.keepStructure should be(false)
            Directives.complete(StatusCodes.Accepted)

          case None =>
            Directives.complete(StatusCodes.OK)
        }
      }
    )

    Get("/") ~> route ~> check {
      status should be(StatusCodes.OK)
    }

    Get(s"/?dest=$expectedPath&keep=false") ~> route ~> check {
      status should be(StatusCodes.Accepted)
    }
  }

  they should "extract operation state from string" in withRetry {
    import stasis.client.api.http.routes.Operations.State

    State(state = "active") should be(State.Active)
    State(state = "completed") should be(State.Completed)
    State(state = "all") should be(State.All)

    an[IllegalArgumentException] should be thrownBy State(state = "other")
  }

  private val matchingFileNameRegexes = Seq(
    """test-file""",
    """test-file\.json""",
    """.*"""
  )

  private val matchingPathRegexes = Seq(
    """/tmp/.*""",
    """/.*/a/.*/c"""
  )

  def createRoutes(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    executor: MockOperationExecutor = MockOperationExecutor(),
    scheduler: MockOperationScheduler = MockOperationScheduler(),
    trackers: MockTrackerViews = MockTrackerViews()
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      trackers = trackers,
      search = MockSearch(),
      handlers = Context.Handlers(
        terminateService = () => (),
        verifyUserPassword = _ => false,
        updateUserCredentials = (_, _) => Future.successful(Done),
        reEncryptDeviceSecret = _ => Future.successful(Done)
      ),
      commandProcessor = MockCommandProcessor(),
      secretsConfig = Fixtures.Secrets.DefaultConfig,
      analytics = new MockAnalyticsCollector,
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new Operations().routes()
  }
}

object OperationsSpec {
  import play.api.libs.json._

  final case class PartialBackupState(
    operation: Operation.Id,
    entities: PartialBackupState.Entities,
    metadataCollected: Option[Instant],
    metadataPushed: Option[Instant],
    failures: Seq[String],
    completed: Option[Instant]
  )

  object PartialBackupState {
    final case class Entities(
      discovered: Seq[String],
      unmatched: Seq[String],
      examined: Seq[String],
      collected: Seq[String],
      pending: Map[String, JsObject],
      processed: Map[String, JsObject],
      failed: Map[String, String]
    )
  }

  final case class PartialRecoveryState(
    operation: Operation.Id,
    entities: PartialRecoveryState.Entities,
    failures: Seq[String],
    completed: Option[Instant]
  )

  object PartialRecoveryState {
    final case class Entities(
      examined: Seq[String],
      collected: Seq[String],
      pending: Map[String, JsObject],
      processed: Map[String, JsObject],
      metadataApplied: Seq[String],
      failed: Map[String, String]
    )
  }

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val partialBackupStateEntitiesFormat: Reads[PartialBackupState.Entities] =
    Json.reads[PartialBackupState.Entities]

  implicit val partialBackupStateFormat: Reads[PartialBackupState] =
    Json.reads[PartialBackupState]

  implicit val partialRecoveryStateEntitiesFormat: Reads[PartialRecoveryState.Entities] =
    Json.reads[PartialRecoveryState.Entities]

  implicit val partialRecoveryStateFormat: Reads[PartialRecoveryState] =
    Json.reads[PartialRecoveryState]
}
