package stasis.networking

import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait EndpointClient[A <: EndpointAddress, C] {

  protected val credentials: EndpointCredentials[A, C]

  def push(
    address: A,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done]

  def pull(
    address: A,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]]
}
