package stasis.core.networking

import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.security.NodeCredentialsProvider

trait EndpointClient[A <: EndpointAddress, C] {

  protected def credentials: NodeCredentialsProvider[A, C]

  def push(address: A, manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def push(address: A, manifest: Manifest): Future[Sink[ByteString, Future[Done]]]
  def pull(address: A, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
  def discard(address: A, crate: Crate.Id): Future[Boolean]
}
