package stasis.test.specs.unit.client.service.components

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Promise

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.concurrent.Eventually
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.service.ApplicationTray
import stasis.client.service.components.Base
import stasis.client.service.components.Init
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class InitSpec extends AsyncUnitSpec with ResourceHelpers with AsyncMockitoSugar with Eventually {
  "An Init component" should "support credentials retrieval from StdIn" in {
    val expectedUsername = "test-username"
    val expectedPassword = "test-password"

    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn(expectedUsername)
    when(console.readPassword("Password: ")).thenReturn(expectedPassword.toCharArray)

    for {
      init <- Init(
        base = Base(
          applicationDirectory = createApplicationDirectory(_ => ()),
          applicationTray = ApplicationTray.NoOp(),
          terminate = () => ()
        ).await,
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
        base = Base(
          applicationDirectory = createApplicationDirectory(_ => ()),
          applicationTray = ApplicationTray.NoOp(),
          terminate = () => ()
        ).await,
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

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "InitSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
