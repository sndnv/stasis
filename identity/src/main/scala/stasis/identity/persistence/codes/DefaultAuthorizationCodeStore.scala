package stasis.identity.persistence.codes
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem

import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.Metrics
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.TelemetryContext

class DefaultAuthorizationCodeStore(
  override val name: String,
  expiration: FiniteDuration,
  backend: KeyValueStore[AuthorizationCode, StoredAuthorizationCode]
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends AuthorizationCodeStore {
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] = backend.init()

  override def drop(): Future[Done] = backend.drop()

  override def put(storedCode: StoredAuthorizationCode): Future[Done] = metrics.recordPut(store = name) {
    backend
      .put(storedCode.code, storedCode)
      .map { result =>
        val _ = org.apache.pekko.pattern.after(expiration)(backend.delete(storedCode.code))
        result
      }
  }

  override def delete(code: AuthorizationCode): Future[Boolean] = metrics.recordDelete(store = name) {
    backend.delete(code)
  }

  override def get(code: AuthorizationCode): Future[Option[StoredAuthorizationCode]] = metrics.recordGet(store = name) {
    backend.get(code)
  }

  override def all: Future[Seq[StoredAuthorizationCode]] = metrics.recordList(store = name) {
    backend.entries.map(_.values.toSeq)
  }
}
