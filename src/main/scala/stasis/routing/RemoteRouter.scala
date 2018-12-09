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
import scala.util.control.NonFatal

class RemoteRouter[A <: EndpointAddress, C](
  client: EndpointClient[A, C],
  manifestStore: ManifestStore,
  nodeStore: NodeStore[A]
)(implicit system: ActorSystem)
    extends Router {

  private implicit val ec: ExecutionContext = system.dispatcher

  private val log = Logging(system, this.getClass.getName)

  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    nodeStore.list.flatMap {
      case availableNodes if availableNodes.nonEmpty =>
        Future
          .sequence(
            RemoteRouter.distributeCopies(availableNodes, manifest.copies).map {
              case (node, copies) =>
                log.debug(
                  "Distributing [{}] copies of crate [{}] on node [{}]",
                  copies,
                  manifest.crate,
                  node
                )

                nodeStore.addressOf(node).flatMap {
                  case Some(nodeAddress) =>
                    client
                      .push(nodeAddress, manifest.copy(copies = copies), content)
                      .map { result =>
                        log.debug(
                          "Push of crate [{}] to node [{}] with address [{}] completed: [{}]",
                          manifest.crate,
                          node,
                          nodeAddress,
                          result
                        )

                        Some(node)
                      }
                      .recover {
                        case NonFatal(e) =>
                          log.error(
                            "Push of crate [{}] to node [{}] with address [{}] failed: [{}]",
                            manifest.crate,
                            node,
                            nodeAddress,
                            e
                          )

                          None
                      }

                  case None =>
                    log.error(
                      "Push of crate [{}] to node [{}] failed; unable to retrieve node address",
                      manifest.crate,
                      node
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
          nodeStore.addressOf(node).flatMap {
            case Some(nodeAddress) =>
              client
                .pull(nodeAddress, crate)
                .flatMap {
                  case Some(content) =>
                    log.debug(
                      "Pull of crate [{}] from node [{}] with address [{}] completed",
                      crate,
                      node,
                      nodeAddress
                    )

                    Future.successful(Some(content))

                  case None =>
                    log.warning(
                      "Pull of crate [{}] from node [{}] with address [{}] completed with no content",
                      crate,
                      node,
                      nodeAddress
                    )

                    pullFromNodes(remainingNodes)
                }
                .recoverWith {
                  case NonFatal(e) =>
                    log.error(
                      "Pull of crate [{}] from node [{}] with address [{}] failed: [{}]",
                      crate,
                      node,
                      nodeAddress,
                      e
                    )

                    pullFromNodes(remainingNodes)
                }

            case None =>
              log.error(
                "Pull of crate [{}] from node [{}] failed; unable to retrieve node address",
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
  def distributeCopies(nodes: Seq[Node.Id], copies: Int): Map[Node.Id, Int] =
    if (nodes.nonEmpty && copies > 0) {
      val nodesStream = Stream.continually(nodes).flatten
      val copyIndexes = (0 until copies).seq

      val distribution = nodesStream.zip(copyIndexes).foldLeft(Map.empty[Node.Id, Int]) {
        case (reduced, (node, _)) =>
          reduced + (node -> (reduced.getOrElse(node, 0) + 1))
      }

      distribution
    } else {
      Map.empty
    }
}
