package stasis.core.networking

import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.Future

trait EndpointClient[A <: EndpointAddress, C] {

  protected def credentials: NodeCredentialsProvider[A, C]

  def push(address: A, manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done]
  def push(address: A, manifest: Manifest): Future[Sink[ByteString, Future[Done]]]
  def pull(address: A, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
  def discard(address: A, crate: Crate.Id): Future[Boolean]
}
