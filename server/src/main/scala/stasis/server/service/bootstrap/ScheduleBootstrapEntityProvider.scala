package stasis.server.service.bootstrap

import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config.Config
import org.apache.pekko.Done

import stasis.layers.service.bootstrap.BootstrapEntityProvider
import stasis.server.persistence.schedules.ScheduleStore
import stasis.shared.model.schedules.Schedule

class ScheduleBootstrapEntityProvider(store: ScheduleStore) extends BootstrapEntityProvider[Schedule] {
  override val name: String = "schedules"

  override def default: Seq[Schedule] =
    Seq.empty

  override def load(config: Config): Schedule = {
    val now = Instant.now()
    Schedule(
      id = UUID.fromString(config.getString("id")),
      info = config.getString("info"),
      isPublic = config.getBoolean("public"),
      start = Try(config.getString("start"))
        .filter(_.trim.nonEmpty)
        .toOption
        .map(LocalDateTime.parse)
        .getOrElse(LocalDateTime.now()),
      interval = config.getDuration("interval").toSeconds.seconds,
      created = now,
      updated = now
    )
  }

  override def validate(entities: Seq[Schedule]): Future[Done] =
    requireNonDuplicateField(entities, _.id)

  override def create(entity: Schedule): Future[Done] =
    store.manage().put(entity)

  override def render(entity: Schedule, withPrefix: String): String =
    s"""
       |$withPrefix  schedule:
       |$withPrefix    id:        ${entity.id.toString}
       |$withPrefix    info:      ${entity.info}
       |$withPrefix    is-public: ${entity.isPublic.toString}
       |$withPrefix    start:     ${entity.start.toString}
       |$withPrefix    interval:  ${entity.interval.toCoarsest.toString}
       |$withPrefix    created:   ${entity.created.toString}
       |$withPrefix    updated:   ${entity.updated.toString}""".stripMargin

  override def extractId(entity: Schedule): String =
    entity.id.toString
}
