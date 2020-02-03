package stasis.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.{CreateNode, UpdateNode}
import stasis.shared.api.responses.{CreatedNode, DeletedNode}

import scala.concurrent.Future

class Nodes()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[ServerNodeStore.View.Service] { view =>
              view.list().map { nodes =>
                log.info("User [{}] successfully retrieved [{}] nodes", currentUser, nodes.size)
                discardEntity & complete(nodes.values)
              }
            }
          },
          post {
            entity(as[CreateNode]) { createRequest =>
              resource[ServerNodeStore.Manage.Service] { manage =>
                val node = createRequest.toNode

                manage.create(node).map { _ =>
                  log.info("User [{}] successfully created node [{}]", currentUser, node.id)
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
                  log.info("User [{}] successfully retrieved node [{}]", currentUser, nodeId)
                  discardEntity & complete(node)

                case None =>
                  log.warning("User [{}] failed to retrieve node [{}]", currentUser, nodeId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateNode]) {
              updateRequest =>
                resources[ServerNodeStore.View.Service, ServerNodeStore.Manage.Service] { (view, manage) =>
                  view.get(nodeId).flatMap {
                    case Some(node) =>
                      manage.update(updateRequest.toUpdatedNode(node)).map { _ =>
                        log.info("User [{}] successfully updated node [{}]", currentUser, nodeId)
                        complete(StatusCodes.OK)
                      }

                    case None =>
                      log.warning("User [{}] failed to update missing node [{}]", currentUser, nodeId)
                      Future.successful(complete(StatusCodes.BadRequest))
                  }
                }
            }
          },
          delete {
            resource[ServerNodeStore.Manage.Service] { manage =>
              manage.delete(nodeId).map { deleted =>
                if (deleted) {
                  log.info("User [{}] successfully deleted node [{}]", currentUser, nodeId)
                } else {
                  log.warning("User [{}] failed to delete node [{}]", currentUser, nodeId)
                }

                discardEntity & complete(DeletedNode(existing = deleted))
              }
            }
          }
        )
      }
    )
}