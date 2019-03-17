package stasis.test.specs.unit.core.persistence.backends.ignite
import akka.Done
import akka.util.ByteString
import org.apache.ignite.Ignition
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.ignite.IgniteBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.KeyValueBackendBehaviour

import scala.concurrent.Future

class IgniteBackendSpec extends AsyncUnitSpec with KeyValueBackendBehaviour {
  private class TestIgniteBackend extends KeyValueBackend[String, Int] {
    private val ignition = Ignition.start()
    private val cache = ignition.getOrCreateCache[String, Array[Byte]]("ignition_test_cache")

    private val igniteBackend = new IgniteBackend(
      cache = cache,
      serdes = new KeyValueBackend.Serdes[String, Int] {
        override implicit def serializeKey: String => String = identity
        override implicit def deserializeKey: String => String = identity
        override implicit def serializeValue: Int => ByteString = v => ByteString(BigInt(v).toByteArray)
        override implicit def deserializeValue: ByteString => Int = v => BigInt(v.toArray).toInt
      }
    )

    override def init(): Future[Done] = igniteBackend.init()

    override def drop(): Future[Done] = igniteBackend.drop()

    override def put(key: String, value: Int): Future[Done] = igniteBackend.put(key, value)

    override def get(key: String): Future[Option[Int]] = igniteBackend.get(key)

    override def delete(key: String): Future[Boolean] = igniteBackend.delete(key)

    override def contains(key: String): Future[Boolean] = igniteBackend.contains(key)

    override def entries: Future[Map[String, Int]] = igniteBackend.entries

    def close(): Future[Done] = {
      cache.close()
      ignition.close()
      Future.successful(Done)
    }
  }

  "An IgniteBackend" should behave like keyValueBackend[TestIgniteBackend](
    createBackend = () => new TestIgniteBackend,
    before = _.init(),
    after = _.close()
  )
}
