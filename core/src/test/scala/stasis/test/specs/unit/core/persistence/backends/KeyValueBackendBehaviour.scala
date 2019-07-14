package stasis.test.specs.unit.core.persistence.backends

import akka.Done
import stasis.core.persistence.backends.KeyValueBackend
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.Future

trait KeyValueBackendBehaviour { _: AsyncUnitSpec =>
  def keyValueBackend[B <: KeyValueBackend[String, Int]](
    createBackend: () => B,
    before: B => Future[Done] = (backend: B) => backend.init(),
    after: B => Future[Done] = (backend: B) => backend.drop()
  ): Unit = {
    val testKey = "test-key"
    val testValue = 42

    it should "store and retrieve values" in {
      val store = createBackend()

      for {
        _ <- before(store)
        _ <- store.put(key = testKey, value = testValue)
        existing <- store.get(key = testKey)
        _ <- after(store)
      } yield {
        existing should be(Some(testValue))
      }
    }

    it should "fail to retrieve missing values" in {
      val store = createBackend()

      for {
        _ <- before(store)
        missing <- store.get(key = "missing-key")
        _ <- after(store)
      } yield {
        missing should be(None)
      }
    }

    it should "retrieve all values" in {
      val store = createBackend()

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
      }
    }

    it should "delete values" in {
      val store = createBackend()

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
      }
    }

    it should "check if values exist" in {
      val store = createBackend()

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
      }
    }

    it should "reset itself" in {
      val store = createBackend()

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
      }
    }
  }
}
