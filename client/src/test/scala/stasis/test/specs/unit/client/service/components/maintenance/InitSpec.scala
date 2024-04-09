package stasis.test.specs.unit.client.service.components.maintenance

import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.{Base, Init}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class InitSpec extends AsyncUnitSpec with ResourceHelpers with AsyncMockitoSugar {
  "An Init component" should "support credentials retrieval from StdIn" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance.empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = Some(console))
      (userName, userPassword) <- init.credentials()
    } yield {
      userName should be(expectedUserName)
      userPassword.mkString should be(expectedUserPassword.mkString)
    }
  }

  it should "support credentials retrieval from CLI" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance(
          regenerateApiCertificate = false,
          deviceSecretOperation = None,
          userName = expectedUserName,
          userPassword = expectedUserPassword
        ),
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
      (userName, userPassword) <- init.credentials()
    } yield {
      userName should be(expectedUserName)
      userPassword.mkString should be(expectedUserPassword.mkString)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "InitSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
