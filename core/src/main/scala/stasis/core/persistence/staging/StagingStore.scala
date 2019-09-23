package stasis.core.persistence.staging

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Broadcast, Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.exceptions.StagingFailure
import stasis.core.routing.{Node, NodeProxy}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class StagingStore(
  crateStore: CrateStore,
  nodeProxy: NodeProxy,
  destagingDelay: FiniteDuration
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout) {

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val scheduleStore: MemoryBackend[Crate.Id, Cancellable] =
    MemoryBackend[Crate.Id, Cancellable](name = "schedule-store")

  private val log = Logging(untypedSystem, this.getClass.getName)

  def stage(
    manifest: Manifest,
    destinations: Map[Node, Int],
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    if (destinations.nonEmpty) {
      log.debug("Staging crate [{}] with manifest [{}]", manifest.crate, manifest)

      crateStore.canStore(request = CrateStorageRequest(manifest)).flatMap {
        case true =>
          crateStore.persist(manifest, content).flatMap { _ =>
            log.debug(
              "Scheduling destaging of crate [{}] in [{}] second(s) to [{}] destination(s): [{}]",
              manifest.crate,
              destagingDelay.toSeconds,
              destinations.size,
              destinations
            )

            schedule(
              destagingDelay,
              manifest.crate,
              () => destage(manifest, destinations)
            )
          }

        case false =>
          val message = s"Failed to stage crate [${manifest.crate}]; storage not available"
          log.error(message)
          Future.failed(StagingFailure(message))
      }
    } else {
      val message = s"Failed to stage crate [${manifest.crate}]; no destinations specified"
      log.error(message)
      Future.failed(StagingFailure(message))
    }

  def drop(crate: Crate.Id): Future[Boolean] =
    for {
      cancellableOpt <- scheduleStore.get(crate)
      result <- scheduleStore.delete(crate)
    } yield {
      cancellableOpt match {
        case Some(cancellable) =>
          val cancelResult = cancellable.cancel()
          log.debug(
            "Cancelling destaging completed; schedule already cancelled: [{}]",
            !cancelResult
          )

        case None =>
          log.warning("No destaging schedule found for crate [{}]", crate)
      }

      result
    }

  private def schedule(
    delay: FiniteDuration,
    crate: Crate.Id,
    action: () => Future[Done]
  ): Future[Done] = {
    val cancellable = system.scheduler.scheduleOnce(delay) {
      val _ = action()
        .recover {
          case NonFatal(e) =>
            log.error(s"Scheduled action failed: [$e]")
            Done
        }
    }

    scheduleStore.put(crate, cancellable)
  }

  private def destage(
    manifest: Manifest,
    destinations: Map[Node, Int]
  ): Future[Done] = {
    log.debug(
      "Destaging crate [{}] to [{}] destinations: [{}]",
      manifest.crate,
      destinations.size,
      destinations
    )

    scheduleStore.delete(manifest.crate).flatMap { _ =>
      crateStore
        .retrieve(manifest.crate)
        .flatMap {
          case Some(content) =>
            val destinationSinks =
              Future.sequence(
                destinations.map {
                  case (node, copies) =>
                    nodeProxy.sink(node, manifest.copy(copies = copies))
                }
              )

            destinationSinks.map { sinks =>
              val broadcastSink = sinks match {
                case first :: second :: remaining =>
                  Sink.combine(
                    first,
                    second,
                    remaining: _*
                  )(Broadcast[ByteString](_))

                case single :: Nil =>
                  single.mapMaterializedValue(_ => NotUsed)
              }

              val _ = content.runWith(broadcastSink)

              log.info(
                "Destaging crate [{}] to [{}] destinations complete",
                manifest.crate,
                destinations.size
              )

              Done
            }

          case None =>
            val message = s"Destaging crate [${manifest.crate}] failed: crate content not found"
            log.error(message)
            Future.failed(StagingFailure(message))
        }
    }
  }
}
