package stasis.test.specs.unit.core.persistence.backends.memory

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.KeyValueBackendBehaviour

class MemoryBackendSpec extends AsyncUnitSpec with KeyValueBackendBehaviour {

  "A MemoryBackend" should behave like keyValueBackend[MemoryBackend[String, Int]](
    createBackend = telemetry =>
      MemoryBackend(name = "map-store")(
        s = ActorSystem(
          Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
          "MemoryBackendSpec-Typed"
        ),
        telemetry = telemetry,
        t = timeout
      )
  )
}
