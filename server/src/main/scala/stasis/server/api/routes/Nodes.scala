package stasis.server.api.routes

import scala.concurrent.Future

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.server.persistence.nodes.ServerNodeStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateNode
import stasis.shared.api.requests.UpdateNode
import stasis.shared.api.responses.CreatedNode
import stasis.shared.api.responses.DeletedNode

class Nodes()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[ServerNodeStore.View.Service] { view =>
              view.list().map { nodes =>
                log.debugN("User [{}] successfully retrieved [{}] nodes", currentUser, nodes.size)
                discardEntity & complete(nodes.values)
              }
            }
          },
          post {
            entity(as[CreateNode]) { createRequest =>
              resource[ServerNodeStore.Manage.Service] { manage =>
                val node = createRequest.toNode

                manage.create(node).map { _ =>
                  log.debugN("User [{}] successfully created node [{}]", currentUser, node.id)
                  complete(CreatedNode(node.id))
                }
              }
            }
          }
        )
      },
      path(JavaUUID) { nodeId =>
        concat(
          get {
            resource[ServerNodeStore.View.Service] { view =>
              view.get(nodeId).map {
                case Some(node) =>
                  log.debugN("User [{}] successfully retrieved node [{}]", currentUser, nodeId)
                  discardEntity & complete(node)

                case None =>
                  log.warnN("User [{}] failed to retrieve node [{}]", currentUser, nodeId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateNode]) { updateRequest =>
              resources[ServerNodeStore.View.Service, ServerNodeStore.Manage.Service] { (view, manage) =>
                view.get(nodeId).flatMap {
                  case Some(node) =>
                    manage.update(updateRequest.toUpdatedNode(node)).map { _ =>
                      log.debugN("User [{}] successfully updated node [{}]", currentUser, nodeId)
                      complete(StatusCodes.OK)
                    }

                  case None =>
                    log.warnN("User [{}] failed to update missing node [{}]", currentUser, nodeId)
                    Future.successful(complete(StatusCodes.BadRequest))
                }
              }
            }
          },
          delete {
            resource[ServerNodeStore.Manage.Service] { manage =>
              manage.delete(nodeId).map { deleted =>
                if (deleted) {
                  log.debugN("User [{}] successfully deleted node [{}]", currentUser, nodeId)
                } else {
                  log.warnN("User [{}] failed to delete node [{}]", currentUser, nodeId)
                }

                discardEntity & complete(DeletedNode(existing = deleted))
              }
            }
          }
        )
      }
    )
}

object Nodes {
  def apply()(implicit ctx: RoutesContext): Nodes = new Nodes()
}
