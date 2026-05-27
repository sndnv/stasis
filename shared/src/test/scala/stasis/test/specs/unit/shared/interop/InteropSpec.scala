package stasis.test.specs.unit.shared.interop

import org.scalatest.Assertion
import play.api.libs.json.Format
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import stasis.test.specs.unit.UnitSpec

trait InteropSpec extends UnitSpec {
  def assert[T](domain: String, resource: String, matches: T)(implicit format: Format[T]): Assertion = {
    val json = InteropResources.load(domain = domain, resource = resource)

    json.as[T] should be(matches)
    normalize(Json.toJson(matches)) should be(normalize(json))
  }

  private def normalize(value: JsValue): JsValue =
    value match {
      case JsObject(fields) =>
        JsObject(fields.collect {
          case (k, v) if !ignoredKeys.contains(k) && v != JsNull => k -> normalize(v)
        })
      case JsArray(items) =>
        JsArray(items.map(normalize))
      case other =>
        other
    }

  private val ignoredKeys: Set[String] = Set("next_invocation", "use_query_string")
}
