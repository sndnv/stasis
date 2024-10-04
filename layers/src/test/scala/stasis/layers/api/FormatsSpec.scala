package stasis.layers.api

import java.util.UUID

import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.Uri
import play.api.libs.json.Json

import stasis.layers.UnitSpec

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
}
