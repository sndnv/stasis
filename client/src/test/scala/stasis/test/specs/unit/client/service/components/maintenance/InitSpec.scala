package stasis.test.specs.unit.client.service.components.maintenance

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.Base
import stasis.client.service.components.maintenance.Init
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class InitSpec extends AsyncUnitSpec with ResourceHelpers with AsyncMockitoSugar {
  "An Init component" should "support credentials retrieval from StdIn" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedUserPassword)
    when(console.readPassword("Remote Password (optional): ")).thenReturn(Array.emptyCharArray)

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance.PushDeviceSecret.empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = Some(console))
      mode <- init.retrieveCredentials()
    } yield {
      mode match {
        case mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret =>
          mode.currentUserName should be(expectedUserName)
          mode.currentUserPassword should be(expectedUserPassword)

        case other =>
          fail(s"Unexpected mode encountered: [$other]")
      }
    }
  }

  it should "support credentials retrieval from CLI" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
          currentUserName = expectedUserName,
          currentUserPassword = expectedUserPassword,
          remotePassword = None
        ),
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
      mode <- init.retrieveCredentials()
    } yield {
      mode match {
        case mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret =>
          mode.currentUserName should be(expectedUserName)
          mode.currentUserPassword should be(expectedUserPassword)

        case other =>
          fail(s"Unexpected mode encountered: [$other]")
      }
    }
  }

  it should "fail if invalid arguments are provided" in {
    val result = for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance.Empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
    } yield {
      init
    }

    result.failed.map { e =>
      e.getMessage should include("At least one maintenance flag must be set")
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "InitSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
