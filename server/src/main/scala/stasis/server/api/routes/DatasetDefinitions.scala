package stasis.server.api.routes

import scala.concurrent.Future

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.server.persistence.datasets.DatasetDefinitionStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.DeletedDatasetDefinition

class DatasetDefinitions()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[DatasetDefinitionStore.View.Privileged] { view =>
              view.list().map { definitions =>
                log.debugN("User [{}] successfully retrieved [{}] definitions", currentUser, definitions.size)
                discardEntity & complete(definitions)
              }
            }
          },
          post {
            entity(as[CreateDatasetDefinition]) { createRequest =>
              resource[DatasetDefinitionStore.Manage.Privileged] { manage =>
                val definition = createRequest.toDefinition
                manage.put(definition).map { _ =>
                  log.debugN("User [{}] successfully created definition [{}]", currentUser, definition.id)
                  complete(CreatedDatasetDefinition(definition.id))
                }
              }
            }
          }
        )
      },
      path(JavaUUID) { definitionId =>
        concat(
          get {
            resource[DatasetDefinitionStore.View.Privileged] { view =>
              view.get(definitionId).map {
                case Some(definition) =>
                  log.debugN("User [{}] successfully retrieved definition [{}]", currentUser, definitionId)
                  discardEntity & complete(definition)

                case None =>
                  log.warnN("User [{}] failed to retrieve definition [{}]", currentUser, definitionId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateDatasetDefinition]) { updateRequest =>
              resources[DatasetDefinitionStore.View.Privileged, DatasetDefinitionStore.Manage.Privileged] { (view, manage) =>
                view.get(definitionId).flatMap {
                  case Some(definition) =>
                    manage.put(updateRequest.toUpdatedDefinition(definition)).map { _ =>
                      log.debugN("User [{}] successfully updated definition [{}]", currentUser, definitionId)
                      complete(StatusCodes.OK)
                    }

                  case None =>
                    log.warnN("User [{}] failed to update missing definition [{}]", currentUser, definitionId)
                    Future.successful(complete(StatusCodes.BadRequest))
                }
              }
            }
          },
          delete {
            resource[DatasetDefinitionStore.Manage.Privileged] { manage =>
              manage.delete(definitionId).map { deleted =>
                if (deleted) {
                  log.debugN("User [{}] successfully deleted definition [{}]", currentUser, definitionId)
                } else {
                  log.warnN("User [{}] failed to delete definition [{}]", currentUser, definitionId)
                }

                discardEntity & complete(DeletedDatasetDefinition(existing = deleted))
              }
            }
          }
        )
      },
      pathPrefix("own") {
        concat(
          pathEndOrSingleSlash {
            concat(
              get {
                resources[DeviceStore.View.Self, DatasetDefinitionStore.View.Self] { (deviceView, definitionView) =>
                  deviceView
                    .list(currentUser)
                    .flatMap(devices => definitionView.list(devices.map(_.id)))
                    .map { definitions =>
                      log.debugN("User [{}] successfully retrieved [{}] definitions", currentUser, definitions.size)
                      discardEntity & complete(definitions)
                    }
                }
              },
              post {
                entity(as[CreateDatasetDefinition]) { createRequest =>
                  resources[DeviceStore.View.Self, DatasetDefinitionStore.Manage.Self] { (deviceView, definitionManage) =>
                    val definition = createRequest.toDefinition
                    deviceView
                      .list(currentUser)
                      .flatMap(devices => definitionManage.put(devices.map(_.id), definition))
                      .map { _ =>
                        log.debugN("User [{}] successfully created definition [{}]", currentUser, definition.id)
                        complete(CreatedDatasetDefinition(definition.id))
                      }
                  }
                }
              }
            )
          },
          path(JavaUUID) { definitionId =>
            concat(
              get {
                resources[DeviceStore.View.Self, DatasetDefinitionStore.View.Self] { (deviceView, definitionView) =>
                  deviceView
                    .list(currentUser)
                    .flatMap(devices => definitionView.get(devices.map(_.id), definitionId))
                    .map {
                      case Some(definition) =>
                        log.debugN("User [{}] successfully retrieved definition [{}]", currentUser, definitionId)
                        discardEntity & complete(definition)

                      case None =>
                        log.warnN("User [{}] failed to retrieve definition [{}]", currentUser, definitionId)
                        discardEntity & complete(StatusCodes.NotFound)
                    }
                }
              },
              put {
                entity(as[UpdateDatasetDefinition]) { updateRequest =>
                  resources[
                    DeviceStore.View.Self,
                    DatasetDefinitionStore.View.Self,
                    DatasetDefinitionStore.Manage.Self
                  ] { (deviceView, definitionView, definitionManage) =>
                    deviceView
                      .list(currentUser)
                      .flatMap { devices =>
                        val deviceIds = devices.map(_.id)
                        definitionView.get(deviceIds, definitionId).flatMap {
                          case Some(definition) =>
                            definitionManage
                              .put(deviceIds, updateRequest.toUpdatedDefinition(definition))
                              .map { _ =>
                                log.debugN(
                                  "User [{}] successfully updated definition [{}]",
                                  currentUser,
                                  definitionId
                                )
                                complete(StatusCodes.OK)
                              }

                          case None =>
                            log.warnN(
                              "User [{}] failed to update missing definition [{}]",
                              currentUser,
                              definitionId
                            )
                            Future.successful(complete(StatusCodes.BadRequest))

                        }
                      }
                  }
                }
              },
              delete {
                resources[DeviceStore.View.Self, DatasetDefinitionStore.Manage.Self] { (deviceView, definitionManage) =>
                  deviceView
                    .list(currentUser)
                    .flatMap(devices => definitionManage.delete(devices.map(_.id), definitionId))
                    .map { deleted =>
                      if (deleted) {
                        log.debugN("User [{}] successfully deleted definition [{}]", currentUser, definitionId)
                      } else {
                        log.warnN("User [{}] failed to delete definition [{}]", currentUser, definitionId)
                      }

                      discardEntity & complete(DeletedDatasetDefinition(existing = deleted))
                    }
                }
              }
            )
          }
        )
      }
    )
}

object DatasetDefinitions {
  def apply()(implicit ctx: RoutesContext): DatasetDefinitions = new DatasetDefinitions()
}
