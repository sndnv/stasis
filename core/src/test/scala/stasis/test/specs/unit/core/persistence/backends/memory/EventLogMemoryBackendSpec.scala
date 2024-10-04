package stasis.test.specs.unit.core.persistence.backends.memory

import scala.collection.immutable.Queue
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually

import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.EventLogBackendBehaviour

class EventLogMemoryBackendSpec extends AsyncUnitSpec with EventLogBackendBehaviour with Eventually {
  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
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
