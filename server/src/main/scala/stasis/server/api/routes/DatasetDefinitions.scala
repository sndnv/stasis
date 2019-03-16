package stasis.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests.{CreateDatasetDefinition, UpdateDatasetDefinition}
import stasis.server.api.responses.{CreatedDatasetDefinition, DeletedDatasetDefinition}
import stasis.server.model.datasets.DatasetDefinitionStore
import stasis.server.model.devices.DeviceStore

import scala.concurrent.Future

object DatasetDefinitions extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  def apply()(implicit ctx: RoutesContext): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[DatasetDefinitionStore.View.Privileged] { view =>
              view.list().map { definitions =>
                log.info("User [{}] successfully retrieved [{}] definitions", ctx.user, definitions.size)
                complete(definitions.values)
              }
            }
          },
          post {
            entity(as[CreateDatasetDefinition]) { createRequest =>
              resource[DatasetDefinitionStore.Manage.Privileged] { manage =>
                val definition = createRequest.toDefinition
                manage.create(definition).map { _ =>
                  log.info("User [{}] successfully created definition [{}]", ctx.user, definition.id)
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
                  log.info("User [{}] successfully retrieved definition [{}]", ctx.user, definitionId)
                  complete(definition)

                case None =>
                  log.warning("User [{}] failed to retrieve definition [{}]", ctx.user, definitionId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          put {
            entity(as[UpdateDatasetDefinition]) {
              updateRequest =>
                resources[DatasetDefinitionStore.View.Privileged, DatasetDefinitionStore.Manage.Privileged] {
                  (view, manage) =>
                    view.get(definitionId).flatMap {
                      case Some(definition) =>
                        manage.update(updateRequest.toUpdatedDefinition(definition)).map { _ =>
                          log.info("User [{}] successfully updated definition [{}]", ctx.user, definitionId)
                          complete(StatusCodes.OK)
                        }

                      case None =>
                        log.warning("User [{}] failed to update missing definition [{}]", ctx.user, definitionId)
                        Future.successful(complete(StatusCodes.BadRequest))
                    }
                }
            }
          },
          delete {
            resource[DatasetDefinitionStore.Manage.Privileged] { manage =>
              manage.delete(definitionId).map { deleted =>
                if (deleted) {
                  log.info("User [{}] successfully deleted definition [{}]", ctx.user, definitionId)
                } else {
                  log.warning("User [{}] failed to delete definition [{}]", ctx.user, definitionId)
                }

                complete(DeletedDatasetDefinition(existing = deleted))
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
                    .list(ctx.user)
                    .flatMap(devices => definitionView.list(devices.keys.toSeq))
                    .map { definitions =>
                      log.info("User [{}] successfully retrieved [{}] definitions", ctx.user, definitions.size)
                      complete(definitions.values)
                    }
                }
              },
              post {
                entity(as[CreateDatasetDefinition]) {
                  createRequest =>
                    resources[DeviceStore.View.Self, DatasetDefinitionStore.Manage.Self] {
                      (deviceView, definitionManage) =>
                        val definition = createRequest.toDefinition
                        deviceView
                          .list(ctx.user)
                          .flatMap(devices => definitionManage.create(devices.keys.toSeq, definition))
                          .map { _ =>
                            log.info("User [{}] successfully created definition [{}]", ctx.user, definition.id)
                            complete(CreatedDatasetDefinition(definition.id))
                          }
                    }
                }
              }
            )
          },
          path(JavaUUID) {
            definitionId =>
              concat(
                get {
                  resources[DeviceStore.View.Self, DatasetDefinitionStore.View.Self] {
                    (deviceView, definitionView) =>
                      deviceView
                        .list(ctx.user)
                        .flatMap(devices => definitionView.get(devices.keys.toSeq, definitionId))
                        .map {
                          case Some(definition) =>
                            log.info("User [{}] successfully retrieved definition [{}]", ctx.user, definitionId)
                            complete(definition)

                          case None =>
                            log.warning("User [{}] failed to retrieve definition [{}]", ctx.user, definitionId)
                            complete(StatusCodes.NotFound)
                        }
                  }
                },
                put {
                  entity(as[UpdateDatasetDefinition]) {
                    updateRequest =>
                      resources[
                        DeviceStore.View.Self,
                        DatasetDefinitionStore.View.Self,
                        DatasetDefinitionStore.Manage.Self
                      ] {
                        (deviceView, definitionView, definitionManage) =>
                          deviceView
                            .list(ctx.user)
                            .flatMap {
                              devices =>
                                val deviceIds = devices.keys.toSeq
                                definitionView.get(deviceIds, definitionId).flatMap {
                                  case Some(definition) =>
                                    definitionManage
                                      .update(deviceIds, updateRequest.toUpdatedDefinition(definition))
                                      .map { _ =>
                                        log.info(
                                          "User [{}] successfully updated definition [{}]",
                                          ctx.user,
                                          definitionId
                                        )
                                        complete(StatusCodes.OK)
                                      }

                                  case None =>
                                    log.warning(
                                      "User [{}] failed to update missing definition [{}]",
                                      ctx.user,
                                      definitionId
                                    )
                                    Future.successful(complete(StatusCodes.BadRequest))

                                }
                            }
                      }
                  }
                },
                delete {
                  resources[DeviceStore.View.Self, DatasetDefinitionStore.Manage.Self] {
                    (deviceView, definitionManage) =>
                      deviceView
                        .list(ctx.user)
                        .flatMap(devices => definitionManage.delete(devices.keys.toSeq, definitionId))
                        .map { deleted =>
                          if (deleted) {
                            log.info("User [{}] successfully deleted definition [{}]", ctx.user, definitionId)
                          } else {
                            log.warning("User [{}] failed to delete definition [{}]", ctx.user, definitionId)
                          }

                          complete(DeletedDatasetDefinition(existing = deleted))
                        }
                  }
                }
              )
          }
        )
      }
    )
}
