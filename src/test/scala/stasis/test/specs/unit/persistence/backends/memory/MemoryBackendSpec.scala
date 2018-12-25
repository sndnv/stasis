package stasis.test.specs.unit.persistence.backends.memory

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.persistence.backends.memory.MemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.backends.KeyValueBackendBehaviour

class MemoryBackendSpec extends AsyncUnitSpec with KeyValueBackendBehaviour {

  "A MemoryBackend with typed actor system" should behave like keyValueBackend[MemoryBackend[String, Int]](
    createBackend = () =>
      MemoryBackend.typed(name = "map-store")(
        s =
          ActorSystem(Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol], "MemoryBackendSpec-Typed"),
        t = timeout
    )
  )

  "A MemoryBackend with untyped actor system" should behave like keyValueBackend[MemoryBackend[String, Int]](
    createBackend = () =>
      MemoryBackend.untyped(name = "map-store")(
        s = akka.actor.ActorSystem("MemoryBackendSpec-Untyped"),
        t = timeout
    )
  )
}
