package stasis.layers.persistence

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.UnitSpec
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

trait KeyValueStoreBehaviour { _: UnitSpec =>
  def keyValueStore[B <: KeyValueStore[String, Int]](
    createStore: TelemetryContext => B,
    before: B => Future[Done] = (store: B) => store.init(),
    after: B => Future[Done] = (store: B) => store.drop()
  ): Unit = {
    val testKey = "test-key"
    val testValue = 42

    it should "store and retrieve values" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)

      for {
        _ <- before(store)
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
        _ <- after(store)
      } yield {
        existing should be(Some(testValue))

        telemetry.layers.persistence.keyValue.put should be(1)
        telemetry.layers.persistence.keyValue.get should be(1)
        telemetry.layers.persistence.keyValue.delete should be(0)
      }
    }

    it should "update existing values" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)
      val updatedTestValue = 23

      for {
        _ <- before(store)
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
        _ <- store.put(key = testKey, value = updatedTestValue)
        updated <- store.get(key = testKey)
        _ <- after(store)
      } yield {
        existing should be(Some(testValue))
        updated should be(Some(updatedTestValue))

        telemetry.layers.persistence.keyValue.put should be(2)
        telemetry.layers.persistence.keyValue.get should be(2)
        telemetry.layers.persistence.keyValue.delete should be(0)
      }
    }

    it should "fail to retrieve missing values" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)

      for {
        _ <- before(store)
        missing <- store.get(key = "missing-key")
        _ <- after(store)
      } yield {
        missing should be(None)

        telemetry.layers.persistence.keyValue.put should be(0)
        telemetry.layers.persistence.keyValue.get should be(0)
        telemetry.layers.persistence.keyValue.delete should be(0)
      }
    }

    it should "retrieve all values" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)

      for {
        _ <- before(store)
        _ <- store.put(key = s"$testKey-0", value = testValue)
        _ <- store.put(key = s"$testKey-1", value = testValue + 1)
        _ <- store.put(key = s"$testKey-2", value = testValue + 2)
        existing <- store.entries
        _ <- after(store)
      } yield {
        existing should be(
          Map(
            s"$testKey-0" -> testValue,
            s"$testKey-1" -> (testValue + 1),
            s"$testKey-2" -> (testValue + 2)
          )
        )

        telemetry.layers.persistence.keyValue.put should be(3)
        telemetry.layers.persistence.keyValue.get should be(3)
        telemetry.layers.persistence.keyValue.delete should be(0)
      }
    }

    it should "delete values" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)

      for {
        _ <- before(store)
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
        deleteResult <- store.delete(key = testKey)
        missing <- store.get(key = testKey)
        _ <- after(store)
      } yield {
        existing should be(Some(testValue))
        deleteResult should be(true)
        missing should be(None)

        telemetry.layers.persistence.keyValue.put should be(1)
        telemetry.layers.persistence.keyValue.get should be(1)
        telemetry.layers.persistence.keyValue.delete should be(1)
      }
    }

    it should "check if values exist" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)

      for {
        _ <- before(store)
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.contains(key = testKey)
        _ <- store.delete(key = testKey)
        missing <- store.contains(key = testKey)
        _ <- after(store)
      } yield {
        existing should be(true)
        missing should be(false)

        telemetry.layers.persistence.keyValue.put should be(1)
        telemetry.layers.persistence.keyValue.get should be(0)
        telemetry.layers.persistence.keyValue.delete should be(1)
      }
    }

    it should "reset itself" in withRetry {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createStore(telemetry)

      for {
        _ <- before(store)
        _ <- store.put(key = s"$testKey-0", value = testValue)
        _ <- store.put(key = s"$testKey-1", value = testValue + 1)
        _ <- store.put(key = s"$testKey-2", value = testValue + 2)
        existing <- store.entries
        _ <- store.drop()
        _ <- store.init()
        missing <- store.entries
        _ <- after(store)
      } yield {
        existing should be(
          Map(
            s"$testKey-0" -> testValue,
            s"$testKey-1" -> (testValue + 1),
            s"$testKey-2" -> (testValue + 2)
          )
        )

        missing should be(Map.empty)

        telemetry.layers.persistence.keyValue.put should be(3)
        telemetry.layers.persistence.keyValue.get should be(3)
        telemetry.layers.persistence.keyValue.delete should be(0)
      }
    }
  }
}
