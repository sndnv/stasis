package stasis.core.routing

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl.{Broadcast, GraphDSL, RunnableGraph, Source}
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import org.slf4j.LoggerFactory
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.exceptions.ReservationFailure
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.persistence.nodes.NodeStoreView
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.exceptions.{DiscardFailure, DistributionFailure, PullFailure, PushFailure}
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class DefaultRouter(
  routerId: Node.Id,
  persistence: DefaultRouter.Persistence,
  nodeProxy: NodeProxy
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext)
    extends Router {

  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private val router = routerId.toString
  private val metrics = telemetry.metrics[Metrics.Router]

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    persistence.nodes.nodes.flatMap { availableNodes =>
      DefaultRouter.distributeCopies(availableNodes.values.toSeq, manifest) match {
        case Success(distribution) =>
          persistence.reservations.delete(manifest.crate, routerId).flatMap { reservationRemoved =>
            if (reservationRemoved) {
              log.debug("Reservation for crate [{}] removed", manifest.crate)

              persistence.staging match {
                case Some(store) =>
                  for {
                    _ <- store.stage(
                      manifest = manifest,
                      destinations = distribution,
                      content = content,
                      viaProxy = nodeProxy
                    )
                    _ <- persistence.manifests.put(manifest.copy(destinations = distribution.keys.map(_.id).toSeq))
                  } yield {
                    log.debug("Initiated staging for crate [{}]", manifest.crate)
                    metrics.recordStage(router = router, bytes = manifest.size)
                    Done
                  }

                case None =>
                  Future
                    .sequence(
                      distribution.map { case (node, copies) =>
                        log.debugN(
                          "Distributing [{}] copies of crate [{}] on node [{}]",
                          copies,
                          manifest.crate,
                          node.id
                        )

                        persistence.reservations.delete(manifest.crate, node.id).flatMap { _ =>
                          nodeProxy
                            .push(node, manifest.copy(copies = copies))
                            .map { sink =>
                              log.debugN(
                                "Content sink retrieved for node [{}] while pushing crate [{}]",
                                node,
                                manifest.crate
                              )

                              Some((sink, node))
                            }
                            .recover { case NonFatal(e) =>
                              log.error(
                                "Failed to retrieve content sink for node [{}] while pushing crate [{}]: [{} - {}]",
                                node,
                                manifest.crate,
                                e.getClass.getSimpleName,
                                e.getMessage
                              )

                              None
                            }
                        }
                      }
                    )
                    .flatMap { results =>
                      val (sinks, destinations) = results.flatten.unzip

                      if (sinks.nonEmpty) {
                        RunnableGraph
                          .fromGraph(GraphDSL.create(sinks.toSeq) { implicit builder => sinkList =>
                            import GraphDSL.Implicits._
                            val broadcast = builder.add(Broadcast[ByteString](sinkList.length))

                            content ~> broadcast
                            sinkList.foreach(sink => broadcast ~> sink)

                            ClosedShape
                          })
                          .mapMaterializedValue(Future.sequence(_))
                          .run()
                          .flatMap { _ =>
                            metrics.recordPush(router = router, bytes = destinations.size * manifest.size)

                            log.debugN("Crate [{}] pushed to [{}] nodes: [{}]", manifest.crate, destinations.size, destinations)

                            persistence.manifests.put(manifest.copy(destinations = destinations.map(_.id).toSeq))
                          }
                      } else {
                        metrics.recordPushFailure(router = router, Metrics.Router.PushFailure.NoSinks)

                        val message = s"Crate [${manifest.crate.toString}] was not pushed; no content sinks retrieved"
                        log.error(message)
                        Future.failed(PushFailure(message))
                      }
                    }
              }
            } else {
              metrics.recordPushFailure(router = router, Metrics.Router.PushFailure.ReservationNotRemoved)

              val message = s"Push of crate [${manifest.crate.toString}] failed; unable to remove reservation for crate"
              log.error(message)
              Future.failed(PushFailure(message))
            }
          }

        case Failure(e) =>
          metrics.recordPushFailure(router = router, Metrics.Router.PushFailure.DistributionFailed)

          val message = s"Push of crate [${manifest.crate.toString}] failed: [${e.getClass.getSimpleName} - ${e.getMessage}]"
          log.error(message)
          Future.failed(PushFailure(message))
      }
    }

  override def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    def pullFromNodes(
      nodes: List[(Node.Id, Option[Node])],
      manifest: Manifest
    ): Future[Option[Source[ByteString, NotUsed]]] =
      nodes match {
        case (currentNodeId, currentNode) :: remainingNodes =>
          currentNode match {
            case Some(node) =>
              nodeProxy
                .pull(node, manifest.crate)
                .flatMap {
                  case Some(content) =>
                    log.debugN(
                      "Pull of crate [{}] from node [{}] completed",
                      manifest.crate,
                      node
                    )

                    metrics.recordPull(router = router, bytes = manifest.size)

                    Future.successful(Some(content))

                  case None =>
                    log.warnN(
                      "Pull of crate [{}] from node [{}] completed with no content",
                      manifest.crate,
                      node
                    )

                    metrics.recordPullFailure(router = router, reason = Metrics.Router.PullFailure.NoContent)

                    pullFromNodes(remainingNodes, manifest)
                }
                .recoverWith { case NonFatal(e) =>
                  log.error(
                    "Pull of crate [{}] from node [{}] failed: [{} - {}]",
                    manifest.crate,
                    node,
                    e.getClass.getSimpleName,
                    e.getMessage
                  )

                  metrics.recordPullFailure(router = router, reason = Metrics.Router.PullFailure.Exception)

                  pullFromNodes(remainingNodes, manifest)
                }

            case None =>
              log.errorN(
                "Pull of crate [{}] from node [{}] failed; node not found",
                manifest.crate,
                currentNodeId
              )

              metrics.recordPullFailure(router = router, reason = Metrics.Router.PullFailure.MissingNode)

              pullFromNodes(remainingNodes, manifest)
          }

        case Nil =>
          log.error(
            "Pull of crate [{}] failed; no nodes remaining",
            manifest.crate
          )

          metrics.recordPullFailure(router = router, reason = Metrics.Router.PullFailure.NoNodes)

          Future.successful(None)
      }

    persistence.manifests.get(crate).flatMap {
      case Some(manifest) =>
        if (manifest.destinations.nonEmpty) {
          log.debugN(
            "Pulling crate [{}] from [{}] nodes: [{}]",
            crate,
            manifest.destinations.size,
            manifest.destinations
          )

          persistence.nodes.nodes.flatMap { availableNodes =>
            val (local, remote) = manifest.destinations
              .map { destination =>
                (destination, availableNodes.get(destination))
              }
              .partition { case (_, nodeOpt) =>
                nodeOpt.exists {
                  case _: Node.Local => true
                  case _             => false
                }
              }

            pullFromNodes((local ++ remote).toList, manifest)
          }
        } else {
          metrics.recordPullFailure(router = router, reason = Metrics.Router.PullFailure.NoDestinations)

          val message = s"Crate [${crate.toString}] was not pulled; no destinations found"
          log.error(message)
          Future.failed(PullFailure(message))
        }

      case None =>
        metrics.recordPullFailure(router = router, reason = Metrics.Router.PullFailure.NoManifest)

        log.warn("Crate [{}] was not pulled; failed to retrieve manifest", crate)
        Future.successful(None)
    }
  }

  override def discard(crate: Crate.Id): Future[Done] =
    persistence.manifests.get(crate).flatMap {
      case Some(manifest) =>
        persistence.staging
          .fold(Future.successful(false))(_.drop(crate))
          .flatMap { droppedFromStaging =>
            if (droppedFromStaging) {
              log.debugN(
                "Dropped staged crate [{}] with [{}] nodes: [{}]",
                crate,
                manifest.destinations.size,
                manifest.destinations
              )

              metrics.recordDiscard(router = router, bytes = manifest.size)

              Future.successful(Done)
            } else {
              if (manifest.destinations.nonEmpty) {
                log.debugN(
                  "Discarding crate [{}] from [{}] nodes: [{}]",
                  crate,
                  manifest.destinations.size,
                  manifest.destinations
                )

                for {
                  availableNodes <- persistence.nodes.nodes
                  results <- discardFromNodes(manifest, availableNodes)
                  _ <- processDiscardResults(manifest, results)
                } yield {
                  Done
                }
              } else {
                metrics.recordDiscardFailure(router = router, reason = Metrics.Router.DiscardFailure.NoDestinations)

                val message = s"Crate [${crate.toString}] was not discarded; no destinations found"
                log.error(message)
                Future.failed(DiscardFailure(message))
              }
            }
          }

      case None =>
        metrics.recordDiscardFailure(router = router, reason = Metrics.Router.DiscardFailure.NoManifest)

        val message = s"Crate [${crate.toString}] was not discarded; failed to retrieve manifest"
        log.error(message)
        Future.failed(DiscardFailure(message))
    }

  override def reserve(request: CrateStorageRequest): Future[Option[CrateStorageReservation]] =
    persistence.nodes.nodes.flatMap { availableNodes =>
      val distributionResult =
        DefaultRouter.distributeCopies(availableNodes.values.toSeq, request) match {
          case Success(distribution) =>
            Future
              .sequence(
                distribution.map { case (node, copies) =>
                  persistence.reservations.existsFor(request.crate, node.id).flatMap { exists =>
                    if (!exists) {
                      val requestForNode = request.copy(copies = copies)

                      nodeProxy.canStore(node, requestForNode).flatMap { storageAvailable =>
                        if (storageAvailable) {
                          val reservation = CrateStorageReservation(requestForNode, target = node.id)
                          persistence.reservations.put(reservation).map { _ =>
                            metrics.recordReserve(router = router, bytes = reservation.copies * reservation.size)

                            Some(reservation)
                          }
                        } else {
                          metrics.recordReserveFailure(router = router, reason = Metrics.Router.ReserveFailure.NoStorage)

                          log.warnN(
                            "Storage request [{}] for crate [{}] cannot be fulfilled; storage not available",
                            requestForNode,
                            requestForNode.crate
                          )
                          Future.successful(None)
                        }
                      }
                    } else {
                      metrics.recordReserveFailure(router = router, reason = Metrics.Router.ReserveFailure.ReservationExists)

                      val message = s"Failed to process reservation request [${request.toString}]; " +
                        s"reservation already exists for crate [${request.crate.toString}]"
                      log.error(message)
                      Future.failed(ReservationFailure(message))
                    }
                  }
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

                  persistence.reservations.put(reservation).map { _ =>
                    metrics.recordReserve(router = router, bytes = 0) // no storage is actually used by the router
                    Some(reservation)
                  }
                } else {
                  metrics.recordReserveFailure(router = router, reason = Metrics.Router.ReserveFailure.ReservationRejected)

                  log.error("Storage reservation failed for request [{}]; request rejected by all nodes", request.id)
                  Future.successful(None)
                }
              }

          case Failure(e) =>
            metrics.recordReserveFailure(router = router, reason = Metrics.Router.ReserveFailure.DistributionFailed)

            Future.failed(e)
        }

      distributionResult.recover { case NonFatal(e) =>
        log.errorN(
          "Storage reservation failed for request [{}]: [{} - {}]",
          request.id,
          e.getClass.getSimpleName,
          e.getMessage
        )
        None
      }
    }

  private def discardFromNodes(
    manifest: Manifest,
    availableNodes: Map[Node.Id, Node]
  ): Future[Seq[(Node.Id, Boolean)]] =
    Future.sequence(
      manifest.destinations.map { destination =>
        availableNodes.get(destination) match {
          case Some(node) =>
            nodeProxy.discard(node, manifest.crate).map(destination -> _)

          case None =>
            log.errorN("Crate [{}] was not discarded from node [{}]; node not found", manifest.crate, destination)
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
      log.errorN("Failed to discard crate [{}] from node [{}]; crate not found", manifest.crate, node._1)
    }

    val destinationsCount = manifest.destinations.size

    dropManifestNodes(manifest, successfulDiscards, destinationsCount).flatMap { _ =>
      if (successful.lengthCompare(destinationsCount) == 0) {
        metrics.recordDiscard(router = router, bytes = destinationsCount * manifest.size)

        log.debugN(
          "Discarded crate [{}] from [{}] nodes: [{}]",
          manifest.crate,
          manifest.destinations.size,
          manifest.destinations
        )

        Future.successful(Done)
      } else {
        metrics.recordDiscardFailure(router = router, reason = Metrics.Router.DiscardFailure.MissingNodeOrCrate)

        val message = s"Crate [${manifest.crate.toString}] was not discarded; crate or nodes missing"
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
      persistence.manifests.delete(manifest.crate).flatMap { _ =>
        if (successful.lengthCompare(destinationsCount) == 0) {
          Future.successful(Done)
        } else {
          persistence.manifests.put(
            manifest.copy(destinations = manifest.destinations.filterNot(successful.contains(_)))
          )
        }
      }
    } else {
      Future.successful(Done)
    }
}

object DefaultRouter {
  final case class Persistence(
    manifests: ManifestStore,
    nodes: NodeStoreView,
    reservations: ReservationStore,
    staging: Option[StagingStore]
  )

  def apply(
    routerId: Node.Id,
    persistence: DefaultRouter.Persistence,
    nodeProxy: NodeProxy
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext): DefaultRouter =
    new DefaultRouter(
      routerId = routerId,
      persistence = persistence,
      nodeProxy = nodeProxy
    )

  def distributeCopies(
    availableNodes: Seq[Node],
    request: CrateStorageRequest
  ): Try[Map[Node, Int]] =
    distributeCopies(
      availableNodes = availableNodes,
      sourceNodes = Seq(request.source, request.origin),
      copies = request.copies
    )

  def distributeCopies(
    availableNodes: Seq[Node],
    manifest: Manifest
  ): Try[Map[Node, Int]] =
    distributeCopies(
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
        .filter(node => node.storageAllowed && !sourceNodes.contains(node.id))
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
    val localNodesStream = LazyList.continually(nodes).flatten
    val copyIndexes = 0 until copies

    val localNodesDistribution = localNodesStream.zip(copyIndexes).foldLeft(Map.empty[Node, Int]) { case (reduced, (node, _)) =>
      reduced + (node -> (reduced.getOrElse(node, 0) + 1))
    }

    localNodesDistribution
  }

  private def distributeCopiesStatically(remoteNodes: Seq[Node], copies: Int): Map[Node, Int] =
    remoteNodes.map { node =>
      node -> copies
    }.toMap
}
