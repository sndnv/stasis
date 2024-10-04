package stasis.test.specs.unit.core.persistence.backends.geode

import scala.concurrent.Future

import org.apache.geode.cache.Region
import org.apache.geode.cache.client.ClientCache
import org.apache.geode.cache.client.ClientCacheFactory
import org.apache.geode.cache.client.ClientRegionShortcut
import org.apache.geode.distributed.ConfigurationProperties
import org.apache.geode.distributed.ServerLauncher
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.geode.GeodeBackend
import stasis.layers.UnitSpec
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.KeyValueStoreBehaviour
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class GeodeBackendSpec extends UnitSpec with KeyValueStoreBehaviour {
  "A GeodeBackend" should behave like keyValueStore[TestGeodeBackend](
    createStore = telemetry => new TestGeodeBackend()(telemetry),
    before = _.init(),
    after = _.close()
  )

  private class TestGeodeBackend(implicit telemetry: TelemetryContext) extends KeyValueStore[String, Int] {

    override val name: String = "GeodeBackendSpec"

    override val migrations: Seq[Migration] = Seq.empty

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

    def close(): Future[Done] =
      drop().map { _ =>
        cache.close()
        serverLauncher.stop()
        Done
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "GeodeBackendSpec"
  )
}
