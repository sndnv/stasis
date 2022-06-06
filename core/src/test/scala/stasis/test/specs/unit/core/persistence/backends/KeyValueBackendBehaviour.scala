package stasis.test.specs.unit.core.persistence.backends

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.Future

trait KeyValueBackendBehaviour { _: AsyncUnitSpec =>
  def keyValueBackend[B <: KeyValueBackend[String, Int]](
    createBackend: TelemetryContext => B,
    before: B => Future[Done] = (backend: B) => backend.init(),
    after: B => Future[Done] = (backend: B) => backend.drop()
  ): Unit = {
    val testKey = "test-key"
    val testValue = 42

    it should "store and retrieve values" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
        _ <- after(store)
      } yield {
        existing should be(Some(testValue))

        telemetry.persistence.keyValue.init should be(1)
        telemetry.persistence.keyValue.drop should be(1)
        telemetry.persistence.keyValue.put should be(1)
        telemetry.persistence.keyValue.get should be(1)
        telemetry.persistence.keyValue.delete should be(0)
      }
    }

    it should "update existing values" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)
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

        telemetry.persistence.keyValue.init should be(1)
        telemetry.persistence.keyValue.drop should be(1)
        telemetry.persistence.keyValue.put should be(2)
        telemetry.persistence.keyValue.get should be(2)
        telemetry.persistence.keyValue.delete should be(0)
      }
    }

    it should "fail to retrieve missing values" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

      for {
        _ <- before(store)
        missing <- store.get(key = "missing-key")
        _ <- after(store)
      } yield {
        missing should be(None)

        telemetry.persistence.keyValue.init should be(1)
        telemetry.persistence.keyValue.drop should be(1)
        telemetry.persistence.keyValue.put should be(0)
        telemetry.persistence.keyValue.get should be(0)
        telemetry.persistence.keyValue.delete should be(0)
      }
    }

    it should "retrieve all values" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

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

        telemetry.persistence.keyValue.init should be(1)
        telemetry.persistence.keyValue.drop should be(1)
        telemetry.persistence.keyValue.put should be(3)
        telemetry.persistence.keyValue.get should be(3)
        telemetry.persistence.keyValue.delete should be(0)
      }
    }

    it should "delete values" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

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

        telemetry.persistence.keyValue.init should be(1)
        telemetry.persistence.keyValue.drop should be(1)
        telemetry.persistence.keyValue.put should be(1)
        telemetry.persistence.keyValue.get should be(1)
        telemetry.persistence.keyValue.delete should be(1)
      }
    }

    it should "check if values exist" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

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

        telemetry.persistence.keyValue.init should be(1)
        telemetry.persistence.keyValue.drop should be(1)
        telemetry.persistence.keyValue.put should be(1)
        telemetry.persistence.keyValue.get should be(0)
        telemetry.persistence.keyValue.delete should be(1)
      }
    }

    it should "reset itself" in {
      val telemetry: MockTelemetryContext = MockTelemetryContext()

      val store = createBackend(telemetry)

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

        telemetry.persistence.keyValue.init should be(2)
        telemetry.persistence.keyValue.drop should be(2)
        telemetry.persistence.keyValue.put should be(3)
        telemetry.persistence.keyValue.get should be(3)
        telemetry.persistence.keyValue.delete should be(0)
      }
    }
  }
}
