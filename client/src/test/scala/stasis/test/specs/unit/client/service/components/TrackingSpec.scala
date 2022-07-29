package stasis.test.specs.unit.client.service.components

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.components.{Base, Tracking}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class TrackingSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Tracking component" should "create itself from config" in {
    val directory = createApplicationDirectory(init = _ => ())

    val base = Base(applicationDirectory = directory, terminate = () => ()).await

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

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "TrackingSpec"
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
