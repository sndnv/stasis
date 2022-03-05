package stasis.identity.api.oauth.directives

import scala.util.{Failure, Success}

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.clients.{Client, ClientStoreView}

trait ClientRetrieval extends EntityDiscardingDirectives {

  protected def log: Logger

  protected def clientStore: ClientStoreView

  def retrieveClient(clientId: Client.Id): Directive1[Client] =
    Directive { inner =>
      onComplete(clientStore.get(clientId)) {
        case Success(Some(client)) if client.active =>
          inner(Tuple1(client))

        case Success(Some(client)) =>
          log.warnN(
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
          log.warnN("Client [{}] was not found", clientId)

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              "The request has missing, invalid or mismatching redirection URI and/or client identifier"
            )
          }

        case Failure(e) =>
          log.errorN(
            "Failed to retrieve client [{}]: [{}]",
            clientId,
            e.getMessage,
            e
          )

          discardEntity {
            complete(
              StatusCodes.InternalServerError
            )
          }
      }
    }
}
