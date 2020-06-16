package stasis.test.specs.unit.core.persistence.backends.geode

import akka.Done
import akka.util.ByteString
import org.apache.geode.cache.Region
import org.apache.geode.cache.client.{ClientCache, ClientCacheFactory, ClientRegionShortcut}
import org.apache.geode.distributed.{ConfigurationProperties, ServerLauncher}
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.geode.GeodeBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.KeyValueBackendBehaviour

import scala.concurrent.Future

class GeodeBackendSpec extends AsyncUnitSpec with KeyValueBackendBehaviour {
  private class TestGeodeBackend extends KeyValueBackend[String, Int] {

    private val serverLauncher: ServerLauncher =
      new ServerLauncher.Builder()
        .setMemberName("geode_test_server")
        .setServerPort(40404)
        .setWorkingDirectory(s"${System.getProperty("user.dir")}/target")
        .set(ConfigurationProperties.START_LOCATOR, "localhost[10334]")
        .build

    locally { val _ = Future { serverLauncher.start() } }

    private val cache: ClientCache =
      new ClientCacheFactory().addPoolLocator("localhost", 10334).create

    private val region: Region[String, Array[Byte]] =
      cache
        .createClientRegionFactory[String, Array[Byte]](ClientRegionShortcut.LOCAL)
        .create("test-geode-backend")

    private val geodeBackend = new GeodeBackend[String, Int](
      region = region,
      serdes = new KeyValueBackend.Serdes[String, Int] {
        override implicit def serializeKey: String => String = identity

        override implicit def deserializeKey: String => String = identity

        override implicit def serializeValue: Int => ByteString = v => ByteString(BigInt(v).toByteArray)

        override implicit def deserializeValue: ByteString => Int = v => BigInt(v.toArray).toInt
      }
    )

    override def init(): Future[Done] = geodeBackend.init()

    override def drop(): Future[Done] = geodeBackend.drop()

    override def put(key: String, value: Int): Future[Done] = geodeBackend.put(key, value)

    override def delete(key: String): Future[Boolean] = geodeBackend.delete(key)

    override def get(key: String): Future[Option[Int]] = geodeBackend.get(key)

    override def contains(key: String): Future[Boolean] = geodeBackend.contains(key)

    override def entries: Future[Map[String, Int]] = geodeBackend.entries

    def close(): Future[Done] = {
      cache.close()
      serverLauncher.stop()
      Future.successful(Done)
    }
  }

  "A GeodeBackend" should behave like keyValueBackend[TestGeodeBackend](
    createBackend = () => new TestGeodeBackend,
    before = _.init(),
    after = _.close()
  )
}
