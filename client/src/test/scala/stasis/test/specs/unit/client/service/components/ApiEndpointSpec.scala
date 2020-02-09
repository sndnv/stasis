package stasis.test.specs.unit.client.service.components

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import stasis.client.api.clients.Clients
import stasis.client.ops.monitoring.ServerMonitor
import stasis.client.ops.scheduling.{OperationExecutor, OperationScheduler}
import stasis.client.ops.search.Search
import stasis.client.service.components._
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

import scala.collection.mutable
import scala.concurrent.duration._

class ApiEndpointSpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApiEndpoint component" should "create itself from config" in {
    val apiEndpointPort = ports.dequeue()
    val apiEndpointConfigEntry = "stasis.client.api.http.port"

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{$apiEndpointConfigEntry: $apiEndpointPort}"""
        )
      }
    )

    ApiEndpoint(
      base = Base(applicationDirectory = directory, terminate = () => ()).await,
      apiClients = new ApiClients {
        override def clients: Clients = Clients(
          api = MockServerApiEndpointClient(),
          core = MockServerCoreEndpointClient()
        )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
      }
    ).map { endpoint =>
      noException should be thrownBy endpoint.api.start()
      java.nio.file.Files.readString(directory.config.get.resolve(Files.ApiToken)) should not be empty
    }
  }

  it should "support terminating the service after a delay" in {
    val apiEndpointPort = ports.dequeue()
    val apiEndpointConfigEntry = "stasis.client.api.http.port"
    val apiTerminationDelay = 250.millis
    val apiTerminationDelayEntry = "stasis.client.api.termination-delay"

    val terminationCounter = new AtomicInteger(0)

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(
          path.resolve(Files.ConfigOverride),
          s"""{
             |$apiTerminationDelayEntry: "${apiTerminationDelay.toMillis} millis",
             |$apiEndpointConfigEntry: $apiEndpointPort
             }""".stripMargin.replaceAll("\n", "")
        )
      }
    )

    val endpoint = ApiEndpoint(
      base = Base(
        applicationDirectory = directory,
        terminate = () => { val _ = terminationCounter.incrementAndGet() }
      ).await,
      apiClients = new ApiClients {
        override def clients: Clients = Clients(
          api = MockServerApiEndpointClient(),
          core = MockServerCoreEndpointClient()
        )
      },
      ops = new Ops {
        override def executor: OperationExecutor = MockOperationExecutor()
        override def scheduler: OperationScheduler = MockOperationScheduler()
        override def monitor: ServerMonitor = MockServerMonitor()
        override def search: Search = MockSearch()
      }
    ).await

    endpoint.api.start()

    val endpointToken = java.nio.file.Files.readString(directory.config.get.resolve(Files.ApiToken))

    val response = Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$apiEndpointPort/service/stop"
        ).addCredentials(credentials = OAuth2BearerToken(token = endpointToken))
      )
      .await

    response.status should be(StatusCodes.Accepted)

    await(delay = apiTerminationDelay / 2, withSystem = typedSystem)
    terminationCounter.get should be(0)

    await(delay = apiTerminationDelay, withSystem = typedSystem)
    terminationCounter.get should be(1)
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "ApiEndpointSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toUntyped

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val log: LoggingAdapter =
    Logging(untypedSystem, this.getClass.getName)

  private val ports: mutable.Queue[Int] = (30000 to 30100).to[mutable.Queue]
}
