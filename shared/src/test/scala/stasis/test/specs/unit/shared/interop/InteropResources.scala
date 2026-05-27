package stasis.test.specs.unit.shared.interop

import scala.io.Source
import scala.util.Using

import play.api.libs.json.JsValue
import play.api.libs.json.Json

object InteropResources {
  def load(domain: String, resource: String): JsValue = {
    val path = s"/interop/$domain/$resource.json"

    Using.resource(
      Option(getClass.getResourceAsStream(path))
        .getOrElse(throw new IllegalStateException(s"Interop resource not found: [$path]"))
    ) { stream =>
      Json.parse(Source.fromInputStream(stream).mkString)
    }
  }
}
