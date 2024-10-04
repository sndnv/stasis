package stasis.test.specs.unit.client.service.components.maintenance

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.Base
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class BaseSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Base component" should "create itself" in {
    val modeArguments = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate

    Base(
      modeArguments = modeArguments,
      applicationDirectory = createApplicationDirectory(init = _ => ())
    ).map { base =>
      base.args should be(modeArguments)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = s"BaseSpec_${java.util.UUID.randomUUID().toString}"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
