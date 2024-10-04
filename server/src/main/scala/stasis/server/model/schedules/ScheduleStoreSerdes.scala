package stasis.server.model.schedules

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import stasis.core.persistence.backends.KeyValueBackend
import stasis.shared.model.schedules.Schedule

object ScheduleStoreSerdes extends KeyValueBackend.Serdes[Schedule.Id, Schedule] {
  import stasis.shared.api.Formats._

  override implicit def serializeKey: Schedule.Id => String =
    _.toString

  override implicit def deserializeKey: String => Schedule.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: Schedule => ByteString =
    schedule => ByteString(Json.toJson(schedule).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Schedule =
    schedule => Json.parse(schedule.decodeString(StandardCharsets.UTF_8)).as[Schedule]
}
