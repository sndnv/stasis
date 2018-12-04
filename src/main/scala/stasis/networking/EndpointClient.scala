package stasis.networking

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.networking.Endpoint.CrateCreated
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.Future

trait EndpointClient {
  def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[CrateCreated]
  def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]]
}
