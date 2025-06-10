package stasis.layers.api

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import stasis.layers.UnitSpec
import stasis.layers.telemetry.ApplicationInformation
import stasis.layers.telemetry.analytics.AnalyticsEntry

class FormatsSpec extends UnitSpec {
  import Formats._

  "Formats" should "convert durations to/from JSON" in {
    val duration = 42.seconds
    val json = "42"

    finiteDurationFormat.writes(duration).toString should be(json)
    finiteDurationFormat.reads(Json.parse(json)).asOpt should be(Some(duration))
  }

  they should "convert UUID maps to/from JSON" in {
    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    val uuid3 = UUID.randomUUID()

    val uuids = Map[UUID, Int](
      uuid1 -> 1,
      uuid2 -> 2,
      uuid3 -> 3
    )

    val json = s"""{"$uuid1":1,"$uuid2":2,"$uuid3":3}"""

    uuidMapFormat[Int].writes(uuids).toString should be(json)
    uuidMapFormat[Int].reads(Json.parse(json)).asOpt should be(Some(uuids))
  }

  they should "convert options to/from JSON" in {
    val existingValue: Option[String] = Some("test-value")
    val existingJson = "\"test-value\""

    optionFormat[String].writes(existingValue).toString should be(existingJson)
    optionFormat[String].reads(Json.parse(existingJson)).asOpt.flatten should be(existingValue)

    optionFormat[String].writes(None).toString should be("null")
    optionFormat[String].reads(Json.parse("null")).asOpt.flatten should be(None)
  }

  they should "convert URIs to/from JSON" in {
    val rawUri = "http://localhost:1234?a=b&c=d&e=1"
    val uri = Uri(rawUri)
    val json = s"""\"$rawUri\""""

    uriFormat.writes(uri).toString should be(json)
    uriFormat.reads(Json.parse(json)).asOpt should be(Some(uri))
  }

  they should "convert ByteStrings to/from JSON" in {
    val original = ByteString("test-string")
    val json = "\"dGVzdC1zdHJpbmc=\""

    byteStringFormat.writes(original).toString() should be(json)
    byteStringFormat.reads(Json.parse(json)).asOpt should be(Some(original))
  }

  they should "support converting analytics entries to/from JSON" in {
    val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    val original = AnalyticsEntry
      .collected(app = ApplicationInformation.none)
      .withEvent(name = "test-event", attributes = Map("a" -> "b", "c" -> "d"))
      .withFailure(message = "Test failure")

    val entry: AnalyticsEntry = original
      .copy(
        created = now,
        updated = now,
        failures = original.failures.map(_.copy(timestamp = now))
      )

    val json =
      s"""
         |{
         |"runtime":{"id":"${entry.runtime.id}","app":"none;none;0","jre":"${entry.runtime.jre}","os":"${entry.runtime.os}"},
         |"events":[{"id":0,"event":"test-event{a='b',c='d'}"}],
         |"failures":[{"message":"Test failure","timestamp":"$now"}],
         |"created":"$now",
         |"updated":"$now",
         |"entry_type":"collected"
         |}""".stripMargin.replaceAll("\n", "").trim

    analyticsEntryFormat.writes(entry).toString should be(json)
    analyticsEntryFormat.reads(Json.parse(json)).asEither should be(Right(entry))
  }
}
