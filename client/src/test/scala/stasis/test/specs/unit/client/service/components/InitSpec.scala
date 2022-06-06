package stasis.test.specs.unit.client.service.components

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, StatusCodes}
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.concurrent.Eventually
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.components.{Base, Init}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

class InitSpec extends AsyncUnitSpec with ResourceHelpers with AsyncMockitoSugar with Eventually {
  "An Init component" should "support credentials retrieval from StdIn" in {
    val expectedUsername = "test-username"
    val expectedPassword = "test-password"

    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn(expectedUsername)
    when(console.readPassword("Password: ")).thenReturn(expectedPassword.toCharArray)

    for {
      init <- Init(
        base = Base(applicationDirectory = createApplicationDirectory(_ => ()), terminate = () => ()).await,
        startup = Future.successful(Done),
        console = Some(console)
      )
      (actualUsername, actualPassword) <- init.credentials()
    } yield {
      actualUsername should be(expectedUsername)
      actualPassword should be(expectedPassword.toCharArray)
    }
  }

  it should "support credentials retrieval from HTTP endpoint" in {
    val expectedUsername = "test-username"
    val expectedPassword = "test-password"

    val initInterface = typedSystem.settings.config.getString("stasis.client.api.init.interface")
    val initPort = typedSystem.settings.config.getInt("stasis.client.api.init.port")

    val startup = Promise[Done]()

    for {
      init <- Init(
        base = Base(applicationDirectory = createApplicationDirectory(_ => ()), terminate = () => ()).await,
        startup = startup.future,
        console = None
      )
      response <- Http()
        .singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = s"http://$initInterface:$initPort/init",
            entity = FormData("username" -> expectedUsername, "password" -> expectedPassword).toEntity
          )
        )
      (actualUsername, actualPassword) <- init.credentials()
      _ = startup.success(Done)
    } yield {
      response.status should be(StatusCodes.Accepted)
      actualUsername should be(expectedUsername)
      actualPassword should be(expectedPassword.toCharArray)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "InitSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
