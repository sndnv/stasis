package stasis.test.specs.unit.core.persistence.backends.slick

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString
import slick.jdbc.H2Profile

import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.slick.SlickBackend
import stasis.layers.UnitSpec
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.KeyValueStoreBehaviour
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class SlickBackendSpec extends UnitSpec with KeyValueStoreBehaviour {
  "A SlickBackend" should behave like keyValueStore[TestSlickBackend](
    createStore = telemetry => new TestSlickBackend()(telemetry),
    before = _.init(),
    after = _.close()
  )

  private class TestSlickBackend(implicit telemetry: TelemetryContext) extends KeyValueStore[String, Int] {
    private val h2db = H2Profile.api.Database.forURL(url = "jdbc:h2:mem:SlickBackendSpec", keepAliveConnection = true)

    override val name: String = "SlickBackendSpec"

    override val migrations: Seq[Migration] = Seq.empty

    private val slickBackend = new SlickBackend[String, Int](
      name = name,
      profile = H2Profile,
      database = h2db,
      serdes = new KeyValueBackend.Serdes[String, Int] {
        override implicit def serializeKey: String => String = identity
        override implicit def deserializeKey: String => String = identity
        override implicit def serializeValue: Int => ByteString = v => ByteString(BigInt(v).toByteArray)
        override implicit def deserializeValue: ByteString => Int = v => BigInt(v.toArray).toInt
      }
    )

    override def init(): Future[Done] = slickBackend.init()

    override def drop(): Future[Done] = slickBackend.drop()

    override def put(key: String, value: Int): Future[Done] = slickBackend.put(key, value)

    override def delete(key: String): Future[Boolean] = slickBackend.delete(key)

    override def get(key: String): Future[Option[Int]] = slickBackend.get(key)

    override def contains(key: String): Future[Boolean] = slickBackend.contains(key)

    override def entries: Future[Map[String, Int]] = slickBackend.entries

    def close(): Future[Done] =
      drop().map { _ =>
        h2db.close()
        Done
      }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "SlickBackendSpec"
  )
}
