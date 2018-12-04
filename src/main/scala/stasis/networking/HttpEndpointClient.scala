package stasis.networking

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.packaging.{Crate, Manifest}
import stasis.security.NodeAuthenticator

import scala.concurrent.{ExecutionContext, Future}

trait HttpEndpointClient extends EndpointClient {

  import Endpoint._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected val endpointUri: String
  protected val authenticator: NodeAuthenticator

  protected implicit val system: ActorSystem

  private implicit lazy val mat: ActorMaterializer = ActorMaterializer()
  private implicit lazy val ec: ExecutionContext = system.dispatcher

  private lazy val log = Logging(system, this.getClass.getName)

  override def push(
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[CrateCreated] = {
    log.debug("Pushing to endpoint [{}] content with manifest [{}]", endpointUri, manifest)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri =
            s"$endpointUri/crate/${manifest.crate}?copies=${manifest.copies}&retention=${manifest.retention.toSeconds}",
          entity = HttpEntity(ContentTypes.`application/octet-stream`, content)
        ).addCredentials(authenticator.provide())
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              Unmarshal(entity).to[CrateCreated].map { response =>
                log.info("Endpoint [{}] responded to push with: [{}]", endpointUri, response)
                response
              }

            case _ =>
              val _ = entity.discardBytes()
              val message = s"Endpoint [$endpointUri] responded to push with unexpected status: [${status.value}]"
              log.warning(message)
              Future.failed(new RuntimeException(message))
          }
      }
  }

  override def pull(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Pulling from endpoint [{}] crate with ID [{}]", endpointUri, crate)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$endpointUri/crate/$crate"
        ).addCredentials(authenticator.provide())
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              log.info("Endpoint [{}] responded to pull with content", endpointUri)
              Future.successful(Some(entity.dataBytes.mapMaterializedValue(_ => NotUsed)))

            case StatusCodes.NotFound =>
              val _ = entity.discardBytes()
              log.warning("Endpoint [{}] responded to pull with no content", endpointUri)
              Future.successful(None)

            case _ =>
              val _ = entity.discardBytes()
              val message = s"Endpoint [$endpointUri] responded to pull with unexpected status: [${status.value}]"
              log.warning(message)
              Future.failed(new RuntimeException(message))
          }
      }
  }
}
