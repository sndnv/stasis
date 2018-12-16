package stasis.persistence.staging

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Broadcast, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.networking.http.HttpEndpointClient
import stasis.packaging.Manifest
import stasis.persistence.CrateStorageRequest
import stasis.persistence.crates.CrateStore
import stasis.persistence.exceptions.StagingFailure
import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class StagingStore(
  crateStore: CrateStore,
  httpClient: HttpEndpointClient,
  destagingDelay: FiniteDuration
)(implicit val system: ActorSystem) {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(system, this.getClass.getName)

  def stage(
    manifest: Manifest,
    destinations: Map[Node, Int],
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    if (destinations.nonEmpty) {
      log.debug("Staging crate [{}] with manifest [{}]", manifest.crate, manifest)

      crateStore.reserve(request = CrateStorageRequest(manifest)).flatMap {
        case Some(reservation) =>
          log.debug("Staging storage reservation for crate [{}] completed: [{}]", manifest.crate, reservation)

          crateStore.persist(manifest, content).map { result =>
            log.debug(
              "Scheduling destaging of crate [{}] in [{}] second(s) to [{}] destination(s): [{}]",
              manifest.crate,
              destagingDelay.toSeconds,
              destinations.size,
              destinations
            )

            schedule(destagingDelay, () => destage(manifest, destinations))
            result
          }

        case None =>
          val message = s"Failed to stage crate [${manifest.crate}]; staging crate store reservation failed"
          log.error(message)
          Future.failed(StagingFailure(message))
      }
    } else {
      val message = s"Failed to stage crate [${manifest.crate}]; no destinations specified"
      log.error(message)
      Future.failed(StagingFailure(message))
    }

  private def schedule(
    delay: FiniteDuration,
    action: () => Future[Done]
  ): Unit = {
    val _ = system.scheduler.scheduleOnce(delay) {
      val _ = action()
        .recover {
          case NonFatal(e) =>
            log.error(s"Scheduled action failed: [$e]")
            Done
        }
    }
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

    crateStore
      .retrieve(manifest.crate)
      .flatMap {
        case Some(content) =>
          val destinationSinks =
            Future.sequence(
              destinations.map {
                case (node, copies) =>
                  node match {
                    case Node.Local(_, store) =>
                      store.sink(manifest.copy(copies = copies))

                    case Node.Remote.Http(_, address) =>
                      httpClient.sink(address, manifest.copy(copies = copies))
                  }
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
