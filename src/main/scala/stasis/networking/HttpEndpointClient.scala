package stasis.networking

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.unmarshalling._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.networking.exceptions.{CredentialsFailure, EndpointFailure}
import stasis.packaging.{Crate, Manifest}

import scala.concurrent.{ExecutionContext, Future}

trait HttpEndpointClient extends EndpointClient[HttpEndpointAddress, HttpCredentials] {

  import Endpoint._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit val system: ActorSystem

  private implicit lazy val mat: ActorMaterializer = ActorMaterializer()
  private implicit lazy val ec: ExecutionContext = system.dispatcher

  private lazy val log = Logging(system, this.getClass.getName)

  override def push(
    address: HttpEndpointAddress,
    manifest: Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[CrateCreated] = {
    log.debug("Pushing to endpoint [{}] content with manifest [{}]", address.uri, manifest)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        Http()
          .singleRequest(
            request = HttpRequest(
              method = HttpMethods.PUT,
              uri =
                s"${address.uri}/crate/${manifest.crate}?copies=${manifest.copies}&retention=${manifest.retention.toSeconds}",
              entity = HttpEntity(ContentTypes.`application/octet-stream`, content)
            ).addCredentials(endpointCredentials)
          )
          .flatMap {
            case HttpResponse(status, _, entity, _) =>
              status match {
                case StatusCodes.OK =>
                  Unmarshal(entity).to[CrateCreated].map { response =>
                    log.info("Endpoint [{}] responded to push with: [{}]", address.uri, response)
                    response
                  }

                case _ =>
                  val _ = entity.discardBytes()
                  val message = s"Endpoint [${address.uri}] responded to push with unexpected status: [${status.value}]"
                  log.warning(message)
                  Future.failed(EndpointFailure(message))
              }
          }

      case None =>
        val message = s"Push to endpoint ${address.uri} failed; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }

  override def pull(
    address: HttpEndpointAddress,
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] = {
    log.debug("Pulling from endpoint [{}] crate with ID [{}]", address.uri, crate)

    credentials.provide(address) match {
      case Some(endpointCredentials) =>
        Http()
          .singleRequest(
            request = HttpRequest(
              method = HttpMethods.GET,
              uri = s"${address.uri}/crate/$crate"
            ).addCredentials(endpointCredentials)
          )
          .flatMap {
            case HttpResponse(status, _, entity, _) =>
              status match {
                case StatusCodes.OK =>
                  log.info("Endpoint [{}] responded to pull with content", address.uri)
                  Future.successful(Some(entity.dataBytes.mapMaterializedValue(_ => NotUsed)))

                case StatusCodes.NotFound =>
                  val _ = entity.discardBytes()
                  log.warning("Endpoint [{}] responded to pull with no content", address.uri)
                  Future.successful(None)

                case _ =>
                  val _ = entity.discardBytes()
                  val message = s"Endpoint [${address.uri}] responded to pull with unexpected status: [${status.value}]"
                  log.warning(message)
                  Future.failed(EndpointFailure(message))
              }
          }

      case None =>
        val message = s"Pull from endpoint ${address.uri} failed; unable to retrieve credentials"
        log.error(message)
        Future.failed(CredentialsFailure(message))
    }
  }
}
