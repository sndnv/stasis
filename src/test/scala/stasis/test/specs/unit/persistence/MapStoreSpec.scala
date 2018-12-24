package stasis.test.specs.unit.persistence

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.persistence.MapStore
import stasis.test.specs.unit.AsyncUnitSpec

class MapStoreSpec extends AsyncUnitSpec {

  "A MapStore" should behave like mapStore(
    systemType = "typed",
    createStore = () =>
      MapStore.typed(name = "map-store")(
        s = ActorSystem(Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol], "MapStoreSpec-Typed"),
        t = timeout
    )
  )

  it should behave like mapStore(
    systemType = "untyped",
    createStore = () =>
      MapStore.untyped(name = "map-store")(
        s = akka.actor.ActorSystem("MapStoreSpec-Untyped"),
        t = timeout
    )
  )

  private def mapStore(systemType: String, createStore: () => MapStore[String, Int]): Unit = {
    val testKey = "test-key"
    val testValue = 42

    it should s"store and retrieve values [$systemType actor system]" in {
      val store = createStore()

      for {
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
      } yield {
        existing should be(Some(testValue))
      }
    }

    it should s"fail to retrieve missing values [$systemType actor system]" in {
      val store = createStore()

      for {
        missing <- store.get(key = "missing-key")
      } yield {
        missing should be(None)
      }
    }

    it should s"retrieve all values [$systemType actor system]" in {
      val store = createStore()

      for {
        _ <- store.put(key = s"$testKey-0", value = testValue)
        _ <- store.put(key = s"$testKey-1", value = testValue + 1)
        _ <- store.put(key = s"$testKey-2", value = testValue + 2)
        existing <- store.map
      } yield {
        existing should be(
          Map(
            s"$testKey-0" -> testValue,
            s"$testKey-1" -> (testValue + 1),
            s"$testKey-2" -> (testValue + 2)
          )
        )
      }
    }

    it should s"delete values [$systemType actor system]" in {
      val store = createStore()

      for {
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
        _ <- store.delete(key = testKey)
        missing <- store.get(key = testKey)
      } yield {
        existing should be(Some(testValue))
        missing should be(None)
      }
    }

    it should s"reset itself [$systemType actor system]" in {
      val store = createStore()

      for {
        _ <- store.put(key = s"$testKey-0", value = testValue)
        _ <- store.put(key = s"$testKey-1", value = testValue + 1)
        _ <- store.put(key = s"$testKey-2", value = testValue + 2)
        existing <- store.map
        _ <- store.reset()
        missing <- store.map
      } yield {
        existing should be(
          Map(
            s"$testKey-0" -> testValue,
            s"$testKey-1" -> (testValue + 1),
            s"$testKey-2" -> (testValue + 2)
          )
        )

        missing should be(Map.empty)
      }
    }
  }
}
