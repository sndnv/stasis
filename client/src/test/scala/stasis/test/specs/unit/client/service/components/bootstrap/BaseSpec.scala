package stasis.test.specs.unit.client.service.components.bootstrap

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.bootstrap.Base
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class BaseSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Base component" should "create itself" in {
    val modeArguments = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = false,
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
    )

    Base(
      modeArguments = modeArguments,
      applicationDirectory = createApplicationDirectory(init = _ => ())
    ).map { base =>
      base.args should be(modeArguments)
      base.templates.config.content should not be empty
      base.templates.rules.content should not be empty
      base.templates.rules.user should not be empty
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "BaseSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
