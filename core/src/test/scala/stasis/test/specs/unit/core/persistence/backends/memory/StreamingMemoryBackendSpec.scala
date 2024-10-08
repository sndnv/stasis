package stasis.test.specs.unit.core.persistence.backends.memory

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class StreamingMemoryBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A StreamingMemoryBackend" should behave like streamingBackend(
    createBackend = telemetry =>
      StreamingMemoryBackend(
        maxSize = 1000,
        maxChunkSize = 8192,
        name = "map-store"
      )(actorSystem, telemetry, timeout),
    alwaysAvailable = true
  )

  it should "provide its info" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = StreamingMemoryBackend(
      maxSize = 1000,
      maxChunkSize = 8192,
      name = "map-store"
    )

    store.info should be("StreamingMemoryBackend(maxSize=1000, maxChunkSize=8192)")
  }

  private implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "StreamingMemoryBackendSpec-Typed"
  )
}
