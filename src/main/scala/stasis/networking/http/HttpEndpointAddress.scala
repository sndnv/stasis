package stasis.networking.http

import akka.http.scaladsl.model.Uri
import stasis.networking.EndpointAddress

final case class HttpEndpointAddress(uri: Uri) extends EndpointAddress

object HttpEndpointAddress {
  def apply(uri: String): HttpEndpointAddress = HttpEndpointAddress(Uri(uri))
}
