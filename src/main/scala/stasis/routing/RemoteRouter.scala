package stasis.routing

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.networking.{EndpointAddress, EndpointClient}
import stasis.packaging.Crate.Id
import stasis.packaging.Manifest
import stasis.persistence.{ManifestStore, NodeStore}
import stasis.routing.exceptions.{PullFailure, PushFailure}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class RemoteRouter[A <: EndpointAddress: ClassTag, C](
  client: EndpointClient[A, C],
  manifestStore: ManifestStore,
  nodeStore: NodeStore
)(implicit system: ActorSystem)
    extends Router {

  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    nodeStore.nodes.flatMap {
      case availableNodes if availableNodes.nonEmpty =>
        Future
          .sequence(
            RemoteRouter.distributeCopies(availableNodes, manifest.copies).map {
              case (node, copies) =>
                log.debug(
                  "Distributing [{}] copies of crate [{}] on node [{}]",
                  copies,
                  manifest.crate,
                  node.id
                )

                node match {
                  case Node.Remote(id, address: A) =>
                    client
                      .push(address, manifest.copy(copies = copies), content)
                      .map { result =>
                        log.debug(
                          "Push of crate [{}] to node [{}] with address [{}] completed: [{}]",
                          manifest.crate,
                          node.id,
                          address,
                          result
                        )

                        Some(id)
                      }
                      .recover {
                        case NonFatal(e) =>
                          log.error(
                            "Push of crate [{}] to node [{}] with address [{}] failed: [{}]",
                            manifest.crate,
                            node.id,
                            address,
                            e
                          )

                          None
                      }

                  case _ =>
                    log.error(
                      "Push of crate [{}] to node [{}] failed; unexpected node type found",
                      manifest.crate,
                      node.id
                    )

                    Future.successful(None)
                }
            }
          )
          .flatMap { results =>
            val destinations = results.flatten.toSeq

            if (destinations.nonEmpty) {
              log.debug("Crate [{}] pushed to [{}] nodes: [{}]", manifest.crate, destinations.size, destinations)
              manifestStore.put(manifest.copy(destinations = destinations))
            } else {
              val message = s"Crate [${manifest.crate}] was not pushed; failed to push to any node"
              log.error(message)
              Future.failed(PushFailure(message))
            }
          }

      case _ =>
        val message = s"Crate [${manifest.crate}] was not pushed; no nodes found"
        log.error(message)
        Future.failed(PushFailure(message))
    }

  override def pull(
    crate: Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    def pullFromNodes(nodes: Seq[Node.Id]): Future[Option[Source[ByteString, NotUsed]]] =
      nodes match {
        case node :: remainingNodes =>
          nodeStore.get(node).flatMap {
            case Some(Node.Remote(_, address: A)) =>
              client
                .pull(address, crate)
                .flatMap {
                  case Some(content) =>
                    log.debug(
                      "Pull of crate [{}] from node [{}] with address [{}] completed",
                      crate,
                      node,
                      address
                    )

                    Future.successful(Some(content))

                  case None =>
                    log.warning(
                      "Pull of crate [{}] from node [{}] with address [{}] completed with no content",
                      crate,
                      node,
                      address
                    )

                    pullFromNodes(remainingNodes)
                }
                .recoverWith {
                  case NonFatal(e) =>
                    log.error(
                      "Pull of crate [{}] from node [{}] with address [{}] failed: [{}]",
                      crate,
                      node,
                      address,
                      e
                    )

                    pullFromNodes(remainingNodes)
                }

            case Some(unexpectedNode) =>
              log.error(
                "Pull of crate [{}] from node [{}] failed; unexpected node type found",
                crate,
                unexpectedNode
              )

              pullFromNodes(remainingNodes)

            case None =>
              log.error(
                "Pull of crate [{}] from node [{}] failed; node not found",
                crate,
                node
              )

              pullFromNodes(remainingNodes)

          }

        case Nil =>
          log.error(
            "Pull of crate [{}] failed; no nodes remaining",
            crate
          )

          Future.successful(None)
      }

    manifestStore.get(crate).flatMap {
      case Some(manifest) =>
        if (manifest.destinations.nonEmpty) {
          log.debug(
            "Pulling crate [{}] from [{}] nodes: [{}]",
            crate,
            manifest.destinations.size,
            manifest.destinations
          )

          pullFromNodes(manifest.destinations)
        } else {
          val message = s"Crate [$crate] was not pulled; no destinations found"
          log.error(message)
          Future.failed(PullFailure(message))
        }

      case None =>
        val message = s"Crate [$crate] was not pulled; failed to retrieve manifest"
        log.error(message)
        Future.failed(PullFailure(message))
    }
  }
}

object RemoteRouter {
  def distributeCopies(nodes: Seq[Node], copies: Int): Map[Node, Int] =
    if (nodes.nonEmpty && copies > 0) {
      val nodesStream = Stream.continually(nodes).flatten
      val copyIndexes = (0 until copies).seq

      val distribution = nodesStream.zip(copyIndexes).foldLeft(Map.empty[Node, Int]) {
        case (reduced, (node, _)) =>
          reduced + (node -> (reduced.getOrElse(node, 0) + 1))
      }

      distribution
    } else {
      Map.empty
    }
}
