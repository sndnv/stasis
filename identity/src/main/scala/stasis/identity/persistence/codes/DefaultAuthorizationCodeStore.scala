package stasis.identity.persistence.codes
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem

import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

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

  override def put(storedCode: StoredAuthorizationCode): Future[Done] =
    backend
      .put(storedCode.code, storedCode)
      .map { result =>
        val _ = org.apache.pekko.pattern.after(expiration)(backend.delete(storedCode.code))
        metrics.recordPut(store = name)
        result
      }

  override def delete(code: AuthorizationCode): Future[Boolean] =
    backend
      .delete(code)
      .map { result =>
        metrics.recordDelete(store = name)
        result
      }

  override def get(code: AuthorizationCode): Future[Option[StoredAuthorizationCode]] =
    backend
      .get(code)
      .map { result =>
        result.foreach(_ => metrics.recordGet(store = name))
        result
      }

  override def all: Future[Seq[StoredAuthorizationCode]] =
    backend.entries
      .map(_.values.toSeq)
      .map { result =>
        if (result.nonEmpty) {
          metrics.recordGet(store = name, entries = result.size)
        }
        result
      }
}
