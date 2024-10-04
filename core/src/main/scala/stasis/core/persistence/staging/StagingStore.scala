package stasis.core.persistence.staging

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.Cancellable
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.stream.scaladsl.Broadcast
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.exceptions.StagingFailure
import stasis.core.persistence.staging.StagingStore.PendingDestaging
import stasis.core.routing.Node
import stasis.core.routing.NodeProxy
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext

class StagingStore(
  crateStore: CrateStore,
  destagingDelay: FiniteDuration
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout
) {
  private implicit val ec: ExecutionContext = system.executionContext

  private val pendingDestagingStore: MemoryStore[Crate.Id, PendingDestaging] =
    MemoryStore[Crate.Id, PendingDestaging](name = "pending-destaging-store")

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  def stage(
    manifest: Manifest,
    destinations: Map[Node, Int],
    content: Source[ByteString, NotUsed],
    viaProxy: NodeProxy
  ): Future[Done] =
    if (destinations.nonEmpty) {
      log.debugN("Staging crate [{}] with manifest [{}]", manifest.crate, manifest)

      crateStore.canStore(request = CrateStorageRequest(manifest)).flatMap {
        case true =>
          crateStore.persist(manifest, content).flatMap { _ =>
            log.debugN(
              "Scheduling destaging of crate [{}] in [{}] second(s) to [{}] destination(s): [{}]",
              manifest.crate,
              destagingDelay.toSeconds,
              destinations.size,
              destinations
            )

            val cancellable = system.classicSystem.scheduler.scheduleOnce(destagingDelay) {
              val _ = destage(manifest, destinations, viaProxy)
                .recover { case NonFatal(e) =>
                  log.error(s"Scheduled action failed: [${e.getClass.getSimpleName} - ${e.getMessage}]")
                  Done
                }
            }

            val staged = Instant.now()

            pendingDestagingStore.put(
              manifest.crate,
              PendingDestaging(
                crate = manifest.crate,
                staged = staged,
                destaged = staged.plusSeconds(destagingDelay.toSeconds),
                cancellable = cancellable
              )
            )
          }

        case false =>
          val message = s"Failed to stage crate [${manifest.crate.toString}]; storage not available"
          log.error(message)
          Future.failed(StagingFailure(message))
      }
    } else {
      val message = s"Failed to stage crate [${manifest.crate.toString}]; no destinations specified"
      log.error(message)
      Future.failed(StagingFailure(message))
    }

  def drop(crate: Crate.Id): Future[Boolean] =
    for {
      stagedCrate <- pendingDestagingStore.get(crate)
      result <- pendingDestagingStore.delete(crate)
    } yield {
      stagedCrate match {
        case Some(PendingDestaging(_, _, _, cancellable)) =>
          val cancelResult = cancellable.cancel()
          log.debug(
            "Cancelling destaging completed; schedule already cancelled: [{}]",
            !cancelResult
          )

        case None =>
          log.warn("No destaging schedule found for crate [{}]", crate)
      }

      result
    }

  def pending: Future[Map[Crate.Id, PendingDestaging]] =
    pendingDestagingStore.entries

  private def destage(
    manifest: Manifest,
    destinations: Map[Node, Int],
    viaProxy: NodeProxy
  ): Future[Done] = {
    log.debugN(
      "Destaging crate [{}] to [{}] destinations: [{}]",
      manifest.crate,
      destinations.size,
      destinations
    )

    pendingDestagingStore.delete(manifest.crate).flatMap { _ =>
      crateStore
        .retrieve(manifest.crate)
        .flatMap {
          case Some(content) =>
            val destinationSinks =
              Future.sequence(
                destinations.map { case (node, copies) =>
                  viaProxy.push(node, manifest.copy(copies = copies))
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

              log.debug(
                "Destaging crate [{}] to [{}] destinations complete",
                manifest.crate,
                destinations.size
              )

              Done
            }

          case None =>
            val message = s"Destaging crate [${manifest.crate.toString}] failed: crate content not found"
            log.error(message)
            Future.failed(StagingFailure(message))
        }
    }
  }
}

object StagingStore {
  final case class PendingDestaging(
    crate: Crate.Id,
    staged: Instant,
    destaged: Instant,
    private val cancellable: Cancellable
  )

  def apply(
    crateStore: CrateStore,
    destagingDelay: FiniteDuration
  )(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): StagingStore =
    new StagingStore(crateStore = crateStore, destagingDelay = destagingDelay)
}
