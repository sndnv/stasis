package stasis.server.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests.{CreateDatasetDefinition, UpdateDatasetDefinition}
import stasis.server.api.responses.{CreatedDatasetDefinition, DeletedDatasetDefinition}
import stasis.server.model.datasets.DatasetDefinitionStore
import stasis.server.model.devices.DeviceStore
import stasis.server.model.users.User
import stasis.server.security.ResourceProvider

import scala.concurrent.ExecutionContext

object DatasetDefinitions {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  def apply(
    resourceProvider: ResourceProvider,
    currentUser: User.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(
              resourceProvider.provide[DatasetDefinitionStore.View.Privileged](currentUser).flatMap(_.list())
            ) { definitions =>
              log.info("User [{}] successfully retrieved [{}] definitions", currentUser, definitions.size)
              complete(definitions.values)
            }
          },
          post {
            entity(as[CreateDatasetDefinition]) { createRequest =>
              val definition = createRequest.toDefinition
              onSuccess(
                resourceProvider
                  .provide[DatasetDefinitionStore.Manage.Privileged](currentUser)
                  .flatMap(_.create(definition))
              ) { _ =>
                log.info("User [{}] successfully created definition [{}]", currentUser, definition.id)
                complete(CreatedDatasetDefinition(definition.id))
              }
            }
          }
        )
      },
      path(JavaUUID) { definitionId =>
        concat(
          get {
            onSuccess(
              resourceProvider.provide[DatasetDefinitionStore.View.Privileged](currentUser).flatMap(_.get(definitionId))
            ) {
              case Some(definition) =>
                log.info("User [{}] successfully retrieved definition [{}]", currentUser, definitionId)
                complete(definition)

              case None =>
                log.warning("User [{}] failed to retrieve definition [{}]", currentUser, definitionId)
                complete(StatusCodes.NotFound)
            }
          },
          put {
            entity(as[UpdateDatasetDefinition]) {
              updateRequest =>
                onSuccess(
                  resourceProvider
                    .provide[DatasetDefinitionStore.View.Privileged](currentUser)
                    .flatMap(_.get(definitionId))
                ) {
                  case Some(definition) =>
                    onSuccess(
                      resourceProvider
                        .provide[DatasetDefinitionStore.Manage.Privileged](currentUser)
                        .flatMap(_.update(updateRequest.toUpdatedDefinition(definition)))
                    ) { _ =>
                      log.info("User [{}] successfully updated definition [{}]", currentUser, definitionId)
                      complete(StatusCodes.OK)
                    }

                  case None =>
                    log.warning("User [{}] failed to update missing definition [{}]", currentUser, definitionId)
                    complete(StatusCodes.BadRequest)
                }
            }
          },
          delete {
            onSuccess(
              resourceProvider
                .provide[DatasetDefinitionStore.Manage.Privileged](currentUser)
                .flatMap(_.delete(definitionId))
            ) { deleted =>
              if (deleted) {
                log.info("User [{}] successfully deleted definition [{}]", currentUser, definitionId)
              } else {
                log.warning("User [{}] failed to delete definition [{}]", currentUser, definitionId)
              }

              complete(DeletedDatasetDefinition(existing = deleted))
            }
          }
        )
      },
      pathPrefix("own") {
        concat(
          pathEndOrSingleSlash {
            concat(
              get {
                onSuccess(
                  for {
                    deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                    devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                    definitionStore <- resourceProvider.provide[DatasetDefinitionStore.View.Self](currentUser)
                    definitions <- definitionStore.list(devices)
                  } yield {
                    definitions
                  }
                ) { definitions =>
                  log.info("User [{}] successfully retrieved [{}] definitions", currentUser, definitions.size)
                  complete(definitions.values)
                }
              },
              post {
                entity(as[CreateDatasetDefinition]) {
                  createRequest =>
                    val definition = createRequest.toDefinition
                    onSuccess(
                      for {
                        deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                        devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                        definitionStore <- resourceProvider.provide[DatasetDefinitionStore.Manage.Self](currentUser)
                        result <- definitionStore.create(devices, definition)
                      } yield {
                        result
                      }
                    ) { _ =>
                      log.info("User [{}] successfully created definition [{}]", currentUser, definition.id)
                      complete(CreatedDatasetDefinition(definition.id))
                    }
                }
              }
            )
          },
          path(JavaUUID) {
            definitionId =>
              concat(
                get {
                  onSuccess(
                    for {
                      deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                      devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                      definitionStore <- resourceProvider.provide[DatasetDefinitionStore.View.Self](currentUser)
                      definition <- definitionStore.get(devices, definitionId)
                    } yield {
                      definition
                    }
                  ) {
                    case Some(definition) =>
                      log.info("User [{}] successfully retrieved definition [{}]", currentUser, definitionId)
                      complete(definition)

                    case None =>
                      log.warning("User [{}] failed to retrieve definition [{}]", currentUser, definitionId)
                      complete(StatusCodes.NotFound)
                  }
                },
                put {
                  entity(as[UpdateDatasetDefinition]) {
                    updateRequest =>
                      onSuccess(
                        for {
                          deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                          devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                          definitionStore <- resourceProvider.provide[DatasetDefinitionStore.View.Self](currentUser)
                          definition <- definitionStore.get(devices, definitionId)
                        } yield {
                          definition.map(d => (d, devices))
                        }
                      ) {
                        case Some((definition, devices)) =>
                          onSuccess(
                            resourceProvider
                              .provide[DatasetDefinitionStore.Manage.Self](currentUser)
                              .flatMap(_.update(devices, updateRequest.toUpdatedDefinition(definition)))
                          ) { _ =>
                            log.info("User [{}] successfully updated definition [{}]", currentUser, definitionId)
                            complete(StatusCodes.OK)
                          }

                        case None =>
                          log.warning("User [{}] failed to update missing definition [{}]", currentUser, definitionId)
                          complete(StatusCodes.BadRequest)
                      }
                  }
                },
                delete {
                  onSuccess(
                    for {
                      deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                      devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                      definitionStore <- resourceProvider.provide[DatasetDefinitionStore.Manage.Self](currentUser)
                      result <- definitionStore.delete(devices, definitionId)
                    } yield {
                      result
                    }
                  ) { deleted =>
                    if (deleted) {
                      log.info("User [{}] successfully deleted definition [{}]", currentUser, definitionId)
                    } else {
                      log.warning("User [{}] failed to delete definition [{}]", currentUser, definitionId)
                    }

                    complete(DeletedDatasetDefinition(existing = deleted))
                  }
                }
              )
          }
        )
      }
    )
}
