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
  "An Init component" should "support current credentials retrieval from StdIn" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedUserPassword)

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance.empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = Some(console))
      (userName, userPassword) <- init.currentCredentials()
    } yield {
      userName should be(expectedUserName)
      userPassword.mkString should be(expectedUserPassword.mkString)
    }
  }

  it should "support current credentials retrieval from CLI" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance(
          regenerateApiCertificate = false,
          deviceSecretOperation = None,
          userCredentialsOperation = None,
          currentUserName = expectedUserName,
          currentUserPassword = expectedUserPassword,
          newUserPassword = Array.emptyCharArray,
          newUserSalt = ""
        ),
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
      (userName, userPassword) <- init.currentCredentials()
    } yield {
      userName should be(expectedUserName)
      userPassword.mkString should be(expectedUserPassword.mkString)
    }
  }

  it should "support new credentials retrieval from StdIn" in {
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserSalt = "test-salt"

    val console = mock[java.io.Console]
    when(console.readPassword("New User Password: ")).thenReturn(expectedUserPassword)
    when(console.readLine("New User Salt: ")).thenReturn(expectedUserSalt)

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance.empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = Some(console))
      (userPassword, userSalt) <- init.newCredentials()
    } yield {
      userPassword.mkString should be(expectedUserPassword.mkString)
      userSalt should be(expectedUserSalt)
    }
  }

  it should "support new credentials retrieval from CLI" in {
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserSalt = "test-salt"

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Maintenance(
          regenerateApiCertificate = false,
          deviceSecretOperation = None,
          userCredentialsOperation = None,
          currentUserName = "",
          currentUserPassword = Array.emptyCharArray,
          newUserPassword = expectedUserPassword,
          newUserSalt = expectedUserSalt
        ),
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
      (userPassword, userSalt) <- init.newCredentials()
    } yield {
      userPassword.mkString should be(expectedUserPassword.mkString)
      userSalt should be(expectedUserSalt)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "InitSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
