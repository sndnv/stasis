package stasis.layers.persistence.memory

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.layers.UnitSpec
import stasis.layers.persistence.KeyValueStoreBehaviour

class MemoryStoreSpec extends UnitSpec with KeyValueStoreBehaviour {
  "A MemoryStore" should behave like keyValueStore[MemoryStore[String, Int]](
    createStore = telemetry =>
      MemoryStore(name = "map-store")(
        s = ActorSystem(guardianBehavior = Behaviors.ignore, name = "MemoryStoreSpec"),
        telemetry = telemetry,
        t = timeout
      )
  )
}
