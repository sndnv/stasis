package stasis.test.specs.unit.client.api.http.routes

import java.time.Instant

import akka.event.Logging
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.client.api.http.Context
import stasis.client.api.http.routes.Operations
import stasis.client.ops.recovery.Recovery.PathQuery
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class OperationsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  "Operations routes" should "provide current operations state" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import Operations._

    val mockTracker = MockTrackerView()
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor, tracker = mockTracker)

    Get("/") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[OperationState]] should not be empty

      mockTracker.statistics(MockTrackerView.Statistic.GetState) should be(1)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetOperations) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support starting backups" in {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()

    Put(s"/backup/$definition") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support starting recoveries with the latest entry for a definition" in {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()

    Put(s"/recover/$definition/latest") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support starting recoveries with the latest entry until a timestamp" in {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()

    Put(s"/recover/$definition/until/${Instant.now()}") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support starting recoveries with a specific entry" in {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val definition = DatasetDefinition.generateId()
    val entry = DatasetEntry.generateId()

    Put(s"/recover/$definition/from/$entry") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(1)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(0)
    }
  }

  they should "support stopping running operations" in {
    val mockExecutor = MockOperationExecutor()
    val routes = createRoutes(executor = mockExecutor)

    val operation = Operation.generateId()

    Put(s"/$operation/stop") ~> routes ~> check {
      status should be(StatusCodes.OK)

      mockExecutor.statistics(MockOperationExecutor.Statistic.GetOperations) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithRules) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartBackupWithFiles) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithDefinition) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartRecoveryWithEntry) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartExpiration) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartValidation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.StartKeyRotation) should be(0)
      mockExecutor.statistics(MockOperationExecutor.Statistic.Stop) should be(1)
    }
  }

  they should "provide support for parameters" in {
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
    tracker: MockTrackerView = MockTrackerView()
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      tracker = tracker,
      search = MockSearch(),
      log = Logging(system, this.getClass.getName)
    )

    new Operations().routes()
  }
}
