package stasis.core.persistence.manifests

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.core.persistence.{Metrics => CoreMetrics}
import stasis.core.routing.Node
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.persistence.{Metrics => LayersMetrics}
import io.github.sndnv.layers.telemetry.TelemetryContext

class DefaultManifestStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ManifestStore {
  import profile.api._
  import system.executionContext

  private val storeMetrics = telemetry.metrics[LayersMetrics.Store]
  private val manifestMetrics = telemetry.metrics[CoreMetrics.ManifestStore]

  private implicit val destinationsColumnType: JdbcType[Seq[Node.Id]] =
    MappedColumnType.base[Seq[Node.Id], String](
      _.map(_.toString).mkString(","),
      _.split(",").map(_.trim).filter(_.nonEmpty).map(java.util.UUID.fromString).toSeq
    )

  private class SlickStore(tag: Tag) extends Table[Manifest](tag, name) {
    def crate: Rep[Crate.Id] = column[Crate.Id]("CRATE", O.PrimaryKey)
    def size: Rep[Long] = column[Long]("SIZE")
    def copies: Rep[Int] = column[Int]("COPIES")
    def origin: Rep[Node.Id] = column[Node.Id]("ORIGIN")
    def source: Rep[Node.Id] = column[Node.Id]("SOURCE")
    def destinations: Rep[Seq[Node.Id]] = column[Seq[Node.Id]]("DESTINATIONS")
    def created: Rep[Instant] = column[Instant]("CREATED")

    def * : ProvenShape[Manifest] =
      (crate, size, copies, origin, source, destinations, created) <> ((Manifest.create _).tupled, Manifest.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(manifest: Manifest): Future[Done] = storeMetrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(manifest))
      .map { _ =>
        manifestMetrics.recordManifest(manifest)
        Done
      }
  }

  override def delete(crate: Crate.Id): Future[Boolean] = storeMetrics.recordDelete(store = name) {
    database
      .run(store.filter(_.crate === crate).delete)
      .map(_ == 1)
  }

  override def get(crate: Crate.Id): Future[Option[Manifest]] = storeMetrics.recordGet(store = name) {
    database.run(store.filter(_.crate === crate).map(_.value).result.headOption)
  }

  override def list(): Future[Seq[Manifest]] = storeMetrics.recordList(store = name) {
    database.run(store.result)
  }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[Manifest, SlickStore](withVersion = 1, current = store) { e =>
        Manifest(
          crate = (e \ "crate").as[Crate.Id],
          size = (e \ "size").as[Long],
          copies = (e \ "copies").as[Int],
          origin = (e \ "origin").as[Node.Id],
          source = (e \ "source").as[Node.Id],
          destinations = (e \ "destinations").as[Seq[Node.Id]],
          created = Instant.now()
        )
      }
  )
}
