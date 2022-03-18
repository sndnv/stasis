package stasis.test.specs.unit.core.persistence.backends.memory

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour

class StreamingMemoryBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A StreamingMemoryBackend" should behave like streamingBackend(
    createBackend = () =>
      StreamingMemoryBackend[java.util.UUID](
        maxSize = 1000,
        maxChunkSize = 8192,
        name = "map-store"
      ),
    alwaysAvailable = true
  )

  it should "provide its info" in {
    val store = StreamingMemoryBackend[java.util.UUID](
      maxSize = 1000,
      maxChunkSize = 8192,
      name = "map-store"
    )

    store.info should be("StreamingMemoryBackend(maxSize=1000, maxChunkSize=8192)")
  }

  private implicit val actorSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "StreamingMemoryBackendSpec-Typed"
  )
}
