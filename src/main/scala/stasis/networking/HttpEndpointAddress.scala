package stasis.networking

import akka.http.scaladsl.model.Uri

final case class HttpEndpointAddress(uri: Uri) extends EndpointAddress

object HttpEndpointAddress {
  def apply(uri: String): HttpEndpointAddress = HttpEndpointAddress(Uri(uri))
}
