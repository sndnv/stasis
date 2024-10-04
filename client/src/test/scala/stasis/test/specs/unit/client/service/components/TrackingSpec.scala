package stasis.test.specs.unit.client.service.components

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.service.ApplicationTray
import stasis.client.service.components.Base
import stasis.client.service.components.Tracking
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class TrackingSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Tracking component" should "create itself from config" in {
    val directory = createApplicationDirectory(init = _ => ())

    val base = Base(applicationDirectory = directory, applicationTray = ApplicationTray.NoOp(), terminate = () => ()).await

    for {
      tracking <- Tracking(base = base)
      backupState <- tracking.trackers.backup.state
      recoveryState <- tracking.trackers.recovery.state
      serverState <- tracking.trackers.server.state
    } yield {
      backupState should be(empty)
      recoveryState should be(empty)
      serverState should be(empty)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "TrackingSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
