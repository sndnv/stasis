package stasis.core.routing

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Broadcast, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.EndpointClientProxy
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.persistence.nodes.NodeStoreView
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.exceptions.{DiscardFailure, DistributionFailure, PullFailure, PushFailure}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class DefaultRouter(
  endpointClient: EndpointClientProxy,
  manifestStore: ManifestStore,
  nodeStore: NodeStoreView,
  reservationStore: ReservationStore,
  stagingStore: Option[StagingStore],
  val routerId: Node.Id
)(implicit system: ActorSystem)
    extends Router {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(system, this.getClass.getName)

  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    nodeStore.nodes.flatMap { availableNodes =>
      DefaultRouter.distributeCopies(availableNodes.values.toSeq, manifest) match {
        case Success(distribution) =>
          reservationStore.delete(manifest.crate, routerId).flatMap { reservationRemoved =>
            if (reservationRemoved) {
              log.debug("Reservation for crate [{}] removed", manifest.crate)

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
                        log.debug("Crate [{}] pushed to [{}] nodes: [{}]",
                                  manifest.crate,
                                  destinations.size,
                                  destinations)
                        manifestStore.put(manifest.copy(destinations = destinations.map(_.id).toSeq))
                      }
                    }
              }
            } else {
              val message = s"Push of crate [${manifest.crate}] failed; unable to remove reservation for crate"
              log.error(message)
              Future.failed(PushFailure(message))
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

  override def discard(crate: Crate.Id): Future[Done] =
    manifestStore.get(crate).flatMap {
      case Some(manifest) =>
        stagingStore
          .fold(Future.successful(false))(_.drop(crate))
          .flatMap { droppedFromStaging =>
            if (droppedFromStaging) {
              log.debug(
                "Dropped staged crate [{}] with [{}] nodes: [{}]",
                crate,
                manifest.destinations.size,
                manifest.destinations
              )

              Future.successful(Done)
            } else {
              if (manifest.destinations.nonEmpty) {
                log.debug(
                  "Discarding crate [{}] from [{}] nodes: [{}]",
                  crate,
                  manifest.destinations.size,
                  manifest.destinations
                )

                for {
                  availableNodes <- nodeStore.nodes
                  results <- discardFromNodes(manifest, availableNodes)
                  _ <- processDiscardResults(manifest, results)
                } yield {
                  Done
                }
              } else {
                val message = s"Crate [$crate] was not discarded; no destinations found"
                log.error(message)
                Future.failed(DiscardFailure(message))
              }
            }
          }

      case None =>
        val message = s"Crate [$crate] was not discarded; failed to retrieve manifest"
        log.error(message)
        Future.failed(DiscardFailure(message))
    }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    nodeStore.nodes.flatMap { availableNodes =>
      val distributionResult =
        DefaultRouter.distributeCopies(availableNodes.values.toSeq, request) match {
          case Success(distribution) =>
            Future
              .sequence(
                distribution.map {
                  case (node, copies) =>
                    reserveOnNode(node, request.copy(copies = copies))
                }
              )
              .flatMap { responses =>
                val reservations = responses.flatten

                if (reservations.nonEmpty) {
                  val reservedCopies = reservations.map(_.copies).sum

                  val reservation = CrateStorageReservation(
                    id = CrateStorageReservation.generateId(),
                    crate = request.crate,
                    size = request.size,
                    copies = math.min(request.copies, reservedCopies),
                    origin = request.origin,
                    target = routerId
                  )

                  reservationStore.put(reservation).map { _ =>
                    Some(reservation)
                  }
                } else {
                  log.error("Storage reservation failed for request [{}]; request rejected by all nodes", request.id)
                  Future.successful(None)
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
      case Node.Local(_, crateStore) =>
        crateStore.sink(manifest.crate)

      case node: Node.Remote[_] =>
        endpointClient.sink(node.address, manifest)
    }

  private def pullFromNode(node: Node, crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    node match {
      case Node.Local(_, crateStore) =>
        crateStore.retrieve(crate)

      case node: Node.Remote[_] =>
        endpointClient.pull(node.address, crate)
    }

  private def reserveOnNode(node: Node, request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    node match {
      case Node.Local(_, crateStore) =>
        crateStore.reserve(request)

      case _: Node.Remote[_] =>
        log.info("Skipping reservation on node [{}]; reserving on remote nodes is not supported", node)
        Future.successful(None)
    }

  private def discardFromNodes(
    manifest: Manifest,
    availableNodes: Map[Node.Id, Node]
  ): Future[Seq[(Node.Id, Boolean)]] =
    Future.sequence(
      manifest.destinations.map { destination =>
        availableNodes.get(destination) match {
          case Some(Node.Local(_, crateStore)) =>
            crateStore.discard(manifest.crate).map(destination -> _)

          case Some(node: Node.Remote[_]) =>
            endpointClient.discard(node.address, manifest.crate).map(destination -> _)

          case None =>
            log.error(s"Crate [${manifest.crate}] was not discarded from node [$destination]; node not found")
            Future.successful(destination -> false)
        }
      }
    )

  private def processDiscardResults(
    manifest: Manifest,
    results: Seq[(Node.Id, Boolean)]
  ): Future[Done] = {
    val (successful, failed) = results.partition(_._2)
    val successfulDiscards = successful.map(_._1)

    failed.foreach { node =>
      log.error("Failed to discard crate [{}] from node [{}]; crate not found", manifest.crate, node._1)
    }

    val destinationsCount = manifest.destinations.size

    dropManifestNodes(manifest, successfulDiscards, destinationsCount).flatMap { _ =>
      if (successful.lengthCompare(destinationsCount) == 0) {
        log.debug(
          "Discarded crate [{}] from [{}] nodes: [{}]",
          manifest.crate,
          manifest.destinations.size,
          manifest.destinations
        )

        Future.successful(Done)
      } else {
        val message = s"Crate [${manifest.crate}] was not discarded; crate or nodes missing"
        log.error(message)
        Future.failed(DiscardFailure(message))
      }
    }
  }

  private def dropManifestNodes(
    manifest: Manifest,
    successful: Seq[Node.Id],
    destinationsCount: Int
  ): Future[Done] =
    if (successful.nonEmpty) {
      manifestStore.delete(manifest.crate).flatMap { _ =>
        if (successful.lengthCompare(destinationsCount) == 0) {
          Future.successful(Done)
        } else {
          manifestStore.put(
            manifest.copy(destinations = manifest.destinations.filterNot(successful.contains(_)))
          )
        }
      }
    } else {
      Future.successful(Done)
    }
}

object DefaultRouter {
  def distributeCopies(
    availableNodes: Seq[Node],
    request: CrateStorageRequest
  ): Try[Map[Node, Int]] = distributeCopies(
    availableNodes = availableNodes,
    sourceNodes = Seq(request.source, request.origin),
    copies = request.copies
  )

  def distributeCopies(
    availableNodes: Seq[Node],
    manifest: Manifest
  ): Try[Map[Node, Int]] = distributeCopies(
    availableNodes = availableNodes,
    sourceNodes = Seq(manifest.source, manifest.origin),
    copies = manifest.copies
  )

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
