package stasis.client.api.clients

import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.core.discovery.ServiceApiClient
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.routing.Node

trait ServerCoreEndpointClient extends ServiceApiClient {
  def self: Node.Id
  def server: String

  def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
