package stasis.core.networking.http

import org.apache.pekko.http.scaladsl.model.Uri

import stasis.core.networking.EndpointAddress

final case class HttpEndpointAddress(uri: Uri) extends EndpointAddress

object HttpEndpointAddress {
  def apply(uri: String): HttpEndpointAddress = HttpEndpointAddress(Uri(uri))
}
