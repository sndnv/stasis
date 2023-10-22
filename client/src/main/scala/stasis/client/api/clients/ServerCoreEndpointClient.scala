package stasis.client.api.clients

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node

import scala.concurrent.Future

trait ServerCoreEndpointClient {
  def self: Node.Id
  def server: String

  def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
