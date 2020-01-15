package stasis.test.specs.unit.core.persistence.backends.memory

import scala.collection.immutable.Queue

import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.Behaviors
import stasis.core.persistence.backends.memory.EventLogMemoryBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.EventLogBackendBehaviour

class EventLogMemoryBackendSpec extends AsyncUnitSpec with EventLogBackendBehaviour {

  "An EventLogMemoryBackend with typed actor system" should behave like
    eventLogBackend[EventLogMemoryBackend[String, Queue[String]]](
      createBackend = () =>
        EventLogMemoryBackend.typed(name = "log-store", initialState = Queue.empty[String])(
          s = ActorSystem(
            guardianBehavior = Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
            name = "EventLogMemoryBackendSpec-Typed"
          ),
          t = timeout
      )
    )

  "An EventLogMemoryBackend with untyped actor system" should behave like
    eventLogBackend[EventLogMemoryBackend[String, Queue[String]]](
      createBackend = () =>
        EventLogMemoryBackend.untyped(name = "log-store", initialState = Queue.empty[String])(
          s = akka.actor.ActorSystem("EventLogMemoryBackendSpec-Untyped"),
          t = timeout
      )
    )
}
