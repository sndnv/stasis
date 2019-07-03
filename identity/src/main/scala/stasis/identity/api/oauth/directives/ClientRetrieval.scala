package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.clients.{Client, ClientStoreView}

import scala.util.{Failure, Success}

trait ClientRetrieval extends BaseApiDirective {

  protected def log: LoggingAdapter

  protected def clientStore: ClientStoreView

  def retrieveClient(clientId: Client.Id): Directive1[Client] =
    Directive { inner =>
      onComplete(clientStore.get(clientId)) {
        case Success(Some(client)) if client.active =>
          inner(Tuple1(client))

        case Success(Some(client)) =>
          log.warning(
            "Retrieval of client [{}] failed; client is not active",
            client.id
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              "The request was made by an inactive client"
            )
          }

        case Success(None) =>
          log.warning("Client [{}] was not found", clientId)

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              "The request has missing, invalid or mismatching redirection URI and/or client identifier"
            )
          }

        case Failure(e) =>
          log.error(
            e,
            "Failed to retrieve client [{}]: [{}]",
            clientId,
            e.getMessage
          )

          discardEntity {
            complete(
              StatusCodes.InternalServerError
            )
          }
      }
    }
}
