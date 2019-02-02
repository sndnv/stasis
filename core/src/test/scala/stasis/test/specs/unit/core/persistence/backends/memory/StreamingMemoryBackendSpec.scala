package stasis.test.specs.unit.core.persistence.backends.memory

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour

class StreamingMemoryBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A StreamingMemoryBackend with typed actor system" should behave like streamingBackend(
    createBackend = () =>
      StreamingMemoryBackend.typed[java.util.UUID](maxSize = 1000, name = "map-store")(
        s = ActorSystem(
          Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
          "StreamingMemoryBackendSpec-Typed"
        ),
        t = timeout
    )
  )

  "A StreamingMemoryBackend with untyped actor system" should behave like streamingBackend(
    createBackend = () =>
      StreamingMemoryBackend.untyped[java.util.UUID](maxSize = 1000, name = "map-store")(
        s = akka.actor.ActorSystem("StreamingMemoryBackendSpec-Untyped"),
        t = timeout
    )
  )
}
