package stasis.identity.api.oauth.directives

import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.slf4j.Logger

import stasis.identity.model.clients.Client
import stasis.identity.persistence.clients.ClientStore
import stasis.layers.api.directives.EntityDiscardingDirectives

trait ClientRetrieval extends EntityDiscardingDirectives {

  protected def log: Logger

  protected def clientStore: ClientStore.View

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
            "Failed to retrieve client [{}]: [{} - {}]",
            clientId,
            e.getClass.getSimpleName,
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
