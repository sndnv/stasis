package stasis.routing

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Broadcast, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.networking.http.{HttpEndpointAddress, HttpEndpointClient}
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.manifests.ManifestStore
import stasis.persistence.nodes.NodeStoreView
import stasis.persistence.staging.StagingStore
import stasis.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.routing.exceptions.{DistributionFailure, PullFailure, PushFailure}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class DefaultRouter(
  httpClient: HttpEndpointClient,
  manifestStore: ManifestStore,
  nodeStore: NodeStoreView,
  stagingStore: Option[StagingStore]
)(implicit system: ActorSystem)
    extends Router {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(system, this.getClass.getName)

  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    nodeStore.nodes.flatMap { availableNodes =>
      DefaultRouter.distributeCopies(
        availableNodes = availableNodes.values.toSeq,
        sourceNodes = Seq(manifest.source, manifest.origin),
        copies = manifest.copies
      ) match {
        case Success(distribution) =>
          stagingStore match {
            case Some(store) =>
              for {
                _ <- store.stage(manifest, destinations = distribution, content)
                _ <- manifestStore.put(manifest.copy(destinations = distribution.keys.map(_.id).toSeq))
              } yield {
                log.debug("Initiated staging for crate [{}]", manifest.crate)
                Done
              }

            case None =>
              Future
                .sequence(
                  distribution.map {
                    case (node, copies) =>
                      log.debug(
                        "Distributing [{}] copies of crate [{}] on node [{}]",
                        copies,
                        manifest.crate,
                        node.id
                      )

                      nodeSink(node, manifest.copy(copies = copies))
                        .map { sink =>
                          log.debug(
                            "Content sink retrieved for node [{}] while pushing crate [{}]",
                            node,
                            manifest.crate
                          )

                          Some((sink, node))
                        }
                        .recover {
                          case NonFatal(e) =>
                            log.error(
                              "Failed to retrieve content sink for node [{}] while pushing crate [{}]: [{}]",
                              node,
                              manifest.crate,
                              e
                            )

                            None
                        }
                  }
                )
                .flatMap { results =>
                  val (sinks, destinations) = results.flatten.unzip

                  val result = sinks match {
                    case first :: second :: remaining =>
                      val _ = content.runWith(Sink.combine(first, second, remaining: _*)(Broadcast[ByteString](_)))
                      Future.successful(Done)

                    case single :: Nil =>
                      content.runWith(single)

                    case Nil =>
                      val message = s"Crate [${manifest.crate}] was not pushed; no content sinks retrieved"
                      log.error(message)
                      Future.failed(PushFailure(message))
                  }

                  result.flatMap { _ =>
                    log.debug("Crate [{}] pushed to [{}] nodes: [{}]", manifest.crate, destinations.size, destinations)
                    manifestStore.put(manifest.copy(destinations = destinations.map(_.id).toSeq))
                  }
                }
          }

        case Failure(e) =>
          val message = s"Push of crate [${manifest.crate}] failed: [$e]"
          log.error(message)
          Future.failed(PushFailure(message))
      }
    }

  override def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    def pullFromNodes(
      nodes: Seq[(Node.Id, Option[Node])],
      crate: Crate.Id
    ): Future[Option[Source[ByteString, NotUsed]]] =
      nodes match {
        case (currentNodeId, currentNode) :: remainingNodes =>
          currentNode match {
            case Some(node) =>
              pullFromNode(node, crate)
                .flatMap {
                  case Some(content) =>
                    log.debug(
                      "Pull of crate [{}] from node [{}] completed",
                      crate,
                      node
                    )

                    Future.successful(Some(content))

                  case None =>
                    log.warning(
                      "Pull of crate [{}] from node [{}] completed with no content",
                      crate,
                      node
                    )

                    pullFromNodes(remainingNodes, crate)
                }
                .recoverWith {
                  case NonFatal(e) =>
                    log.error(
                      "Pull of crate [{}] from node [{}] failed: [{}]",
                      crate,
                      node,
                      e
                    )

                    pullFromNodes(remainingNodes, crate)
                }

            case None =>
              log.error(
                "Pull of crate [{}] from node [{}] failed; node not found",
                crate,
                currentNodeId
              )

              pullFromNodes(remainingNodes, crate)

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

          nodeStore.nodes.flatMap { availableNodes =>
            val (local, remote) = manifest.destinations
              .map { destination =>
                (destination, availableNodes.get(destination))
              }
              .partition {
                case (_, nodeOpt) =>
                  nodeOpt.exists {
                    case _: Node.Local => true
                    case _             => false
                  }
              }

            pullFromNodes(local ++ remote, crate)
          }
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

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    nodeStore.nodes.flatMap { availableNodes =>
      val distributionResult =
        DefaultRouter.distributeCopies(
          availableNodes = availableNodes.values.toSeq,
          sourceNodes = Seq(request.source, request.origin),
          copies = request.copies
        ) match {
          case Success(distribution) =>
            Future
              .sequence(
                distribution.map {
                  case (node, copies) =>
                    reserveOnNode(node, request.copy(copies = copies))
                }
              )
              .map { responses =>
                val reservations = responses.flatten

                if (reservations.nonEmpty) {
                  val reservedCopies = reservations.map(_.copies).sum
                  val minRetention = reservations.map(_.retention.toSeconds).foldLeft(Long.MaxValue)(math.min)
                  val minExpiration = reservations.map(_.expiration.toSeconds).foldLeft(Long.MaxValue)(math.min)

                  Some(
                    CrateStorageReservation(
                      id = CrateStorageReservation.generateId(),
                      size = request.size,
                      copies = math.min(request.copies, reservedCopies),
                      retention = math.min(request.retention.toSeconds, minRetention).seconds,
                      expiration = minExpiration.seconds,
                      origin = request.origin
                    )
                  )
                } else {
                  log.error("Storage reservation failed for request [{}]; request rejected by all nodes", request.id)
                  None
                }
              }

          case Failure(e) =>
            Future.failed(e)
        }

      distributionResult.recover {
        case NonFatal(e) =>
          log.error("Storage reservation failed for request [{}]: [{}]", request.id, e)
          None
      }
    }

  private def nodeSink(node: Node, manifest: Manifest): Future[Sink[ByteString, Future[Done]]] =
    node match {
      case Node.Local(_, store) =>
        store.sink(manifest)

      case Node.Remote.Http(_, address) =>
        httpClient.sink(address, manifest)
    }

  private def pullFromNode(node: Node, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    node match {
      case Node.Local(_, crateStore) =>
        crateStore.retrieve(crate)

      case Node.Remote.Http(_, address: HttpEndpointAddress) =>
        httpClient.pull(address, crate)
    }

  private def reserveOnNode(node: Node, request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    node match {
      case Node.Local(_, crateStore) =>
        crateStore.reserve(request)

      case _: Node.Remote.Http =>
        log.info("Skipping reservation on node [{}]; reserving on remote nodes is not supported", node)
        Future.successful(None)
    }
}

object DefaultRouter {
  def distributeCopies(
    availableNodes: Seq[Node],
    sourceNodes: Seq[Node.Id],
    copies: Int
  ): Try[Map[Node, Int]] =
    if (copies > 0) {
      val (localNodes, remoteNodes) = availableNodes
        .filter(node => !sourceNodes.contains(node.id))
        .partition {
          case _: Node.Local => true
          case _             => false
        }

      if (localNodes.nonEmpty) {
        val localNodesDistribution = distributeCopiesOverNodes(localNodes, copies)
        val remoteNodesDistribution = distributeCopiesStatically(remoteNodes, copies = 1)
        Success(localNodesDistribution ++ remoteNodesDistribution)
      } else if (remoteNodes.nonEmpty) {
        Success(distributeCopiesOverNodes(remoteNodes, copies))
      } else {
        Failure(
          DistributionFailure("No nodes provided")
        )
      }
    } else {
      Failure(
        DistributionFailure("No copies requested")
      )
    }

  private def distributeCopiesOverNodes(nodes: Seq[Node], copies: Int): Map[Node, Int] = {
    val localNodesStream = Stream.continually(nodes).flatten
    val copyIndexes = (0 until copies).seq

    val localNodesDistribution = localNodesStream.zip(copyIndexes).foldLeft(Map.empty[Node, Int]) {
      case (reduced, (node, _)) =>
        reduced + (node -> (reduced.getOrElse(node, 0) + 1))
    }

    localNodesDistribution
  }

  private def distributeCopiesStatically(remoteNodes: Seq[Node], copies: Int): Map[Node, Int] =
    remoteNodes.map { node =>
      node -> copies
    }.toMap
}
