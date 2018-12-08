package stasis.persistence

import akka.Done
import stasis.networking.EndpointAddress
import stasis.routing.Node

import scala.concurrent.Future

trait NodeStore[T <: EndpointAddress] {
  def put(node: Node, address: T): Future[Done]
  def list: Future[Seq[Node]]
  def addressOf(node: Node): Future[Option[T]]
}
