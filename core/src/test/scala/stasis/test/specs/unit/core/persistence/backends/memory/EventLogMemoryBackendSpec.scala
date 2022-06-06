package stasis.test.specs.unit.core.persistence.backends.memory

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.scalatest.concurrent.Eventually
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.EventLogBackendBehaviour

import scala.collection.immutable.Queue
import scala.concurrent.duration._

class EventLogMemoryBackendSpec extends AsyncUnitSpec with EventLogBackendBehaviour with Eventually {
  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    guardianBehavior = Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = "EventLogMemoryBackendSpec"
  )

  "An EventLogMemoryBackend" should behave like
    eventLogBackend[EventLogMemoryBackend[String, Queue[String]]](
      createBackend = telemetry => {
        implicit val t: TelemetryContext = telemetry

        EventLogMemoryBackend(
          name = "log-store",
          initialState = Queue.empty[String]
        )
      }
    )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}
