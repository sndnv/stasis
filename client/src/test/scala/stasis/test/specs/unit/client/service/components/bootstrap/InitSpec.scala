package stasis.test.specs.unit.client.service.components.bootstrap

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.bootstrap.{Base, Init}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class InitSpec extends AsyncUnitSpec with ResourceHelpers with AsyncMockitoSugar {
  "An Init component" should "support bootstrap mode arguments retrieval from StdIn" in {
    val expectedServerBootstrapUrl = "https://test-url"
    val expectedBootstrapCode = "test-code"

    val console = mock[java.io.Console]
    when(console.readLine("Server bootstrap URL: ")).thenReturn(expectedServerBootstrapUrl)
    when(console.readLine("Bootstrap Code: ")).thenReturn(expectedBootstrapCode)

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Bootstrap.empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = Some(console))
      args <- init.arguments()
    } yield {
      args.serverBootstrapUrl should be(expectedServerBootstrapUrl)
      args.bootstrapCode should be(expectedBootstrapCode)
      args.acceptSelfSignedCertificates should be(false)
    }
  }

  it should "support bootstrap mode arguments retrieval from CLI" in {
    val expectedServerBootstrapUrl = "https://test-url"
    val expectedBootstrapCode = "test-code"

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Bootstrap(
          serverBootstrapUrl = expectedServerBootstrapUrl,
          bootstrapCode = expectedBootstrapCode,
          acceptSelfSignedCertificates = true,
          userPassword = Array.emptyCharArray,
          userPasswordConfirm = Array.emptyCharArray
        ),
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
      args <- init.arguments()
    } yield {
      args.serverBootstrapUrl should be(expectedServerBootstrapUrl)
      args.bootstrapCode should be(expectedBootstrapCode)
      args.acceptSelfSignedCertificates should be(true)
    }
  }

  it should "support credentials retrieval from StdIn" in {
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)
    when(console.readPassword("Confirm Password: ")).thenReturn(expectedUserPassword)

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Bootstrap.empty,
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = Some(console))
      userPassword <- init.credentials()
    } yield {
      userPassword.mkString should be(expectedUserPassword.mkString)
    }
  }

  it should "support credentials retrieval from CLI" in {
    val expectedUserPassword = "test-password".toCharArray

    for {
      base <- Base(
        modeArguments = ApplicationArguments.Mode.Bootstrap(
          serverBootstrapUrl = "https://test-url",
          bootstrapCode = "test-code",
          acceptSelfSignedCertificates = true,
          userPassword = expectedUserPassword,
          userPasswordConfirm = expectedUserPassword
        ),
        applicationDirectory = createApplicationDirectory(init = _ => ())
      )
      init <- Init(base = base, console = None)
      userPassword <- init.credentials()
    } yield {
      userPassword.mkString should be(expectedUserPassword.mkString)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "InitSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
