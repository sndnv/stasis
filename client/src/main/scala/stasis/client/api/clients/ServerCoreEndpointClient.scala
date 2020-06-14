package stasis.client.api.clients

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.routing.Node

import scala.concurrent.Future

trait ServerCoreEndpointClient {
  def self: Node.Id
  def server: String

  def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
