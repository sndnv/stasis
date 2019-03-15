package stasis.server.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests.CreateDatasetEntry
import stasis.server.api.responses.{CreatedDatasetEntry, DeletedDatasetEntry}
import stasis.server.model.datasets.DatasetEntryStore
import stasis.server.model.devices.DeviceStore
import stasis.server.model.users.User
import stasis.server.security.ResourceProvider

import scala.concurrent.ExecutionContext

object DatasetEntries {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  def apply(
    resourceProvider: ResourceProvider,
    currentUser: User.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    concat(
      path("for-definition" / JavaUUID) { definitionId =>
        get {
          onSuccess(
            resourceProvider.provide[DatasetEntryStore.View.Privileged](currentUser).flatMap(_.list(definitionId))
          ) { entries =>
            log.info(
              "User [{}] successfully retrieved [{}] entries for definition [{}]",
              currentUser,
              entries.size,
              definitionId
            )
            complete(entries.values)
          }
        }
      },
      path(JavaUUID) { entryId =>
        concat(
          get {
            onSuccess(
              resourceProvider.provide[DatasetEntryStore.View.Privileged](currentUser).flatMap(_.get(entryId))
            ) {
              case Some(entry) =>
                log.info("User [{}] successfully retrieved entry [{}]", currentUser, entryId)
                complete(entry)

              case None =>
                log.warning("User [{}] failed to retrieve entry [{}]", currentUser, entryId)
                complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(
              resourceProvider.provide[DatasetEntryStore.Manage.Privileged](currentUser).flatMap(_.delete(entryId))
            ) { deleted =>
              if (deleted) {
                log.info("User [{}] successfully deleted entry [{}]", currentUser, entryId)
              } else {
                log.warning("User [{}] failed to delete entry [{}]", currentUser, entryId)
              }

              complete(DeletedDatasetEntry(existing = deleted))
            }
          }
        )
      },
      pathPrefix("own") {
        concat(
          path("for-definition" / JavaUUID) {
            definitionId =>
              concat(
                get {
                  onSuccess(
                    for {
                      deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                      devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                      entryStore <- resourceProvider.provide[DatasetEntryStore.View.Self](currentUser)
                      entries <- entryStore.list(devices, definitionId)
                    } yield {
                      entries
                    }
                  ) { entries =>
                    log.info(
                      "User [{}] successfully retrieved [{}] entries for definition [{}]",
                      currentUser,
                      entries.size,
                      definitionId
                    )
                    complete(entries.values)
                  }
                },
                post {
                  entity(as[CreateDatasetEntry]) {
                    case createRequest if createRequest.definition == definitionId =>
                      val entry = createRequest.toEntry

                      onSuccess(
                        for {
                          deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                          devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                          entryStore <- resourceProvider.provide[DatasetEntryStore.Manage.Self](currentUser)
                          result <- entryStore.create(devices, entry)
                        } yield {
                          result
                        }
                      ) { _ =>
                        log.info("User [{}] successfully created entry [{}]", currentUser, entry.id)
                        complete(CreatedDatasetEntry(entry.id))
                      }

                    case createRequest =>
                      log.warning(
                        "User [{}] attempted to create entry for definition [{}] but definition [{}] expected",
                        currentUser,
                        createRequest.definition,
                        definitionId
                      )
                      complete(StatusCodes.BadRequest)
                  }
                }
              )
          },
          path(JavaUUID) {
            entryId =>
              concat(
                get {
                  onSuccess(
                    for {
                      deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                      devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                      entryStore <- resourceProvider.provide[DatasetEntryStore.View.Self](currentUser)
                      entry <- entryStore.get(devices, entryId)
                    } yield {
                      entry
                    }
                  ) {
                    case Some(entry) =>
                      log.info("User [{}] successfully retrieved entry [{}]", currentUser, entryId)
                      complete(entry)

                    case None =>
                      log.warning("User [{}] failed to retrieve entry [{}]", currentUser, entryId)
                      complete(StatusCodes.NotFound)
                  }
                },
                delete {
                  onSuccess(
                    for {
                      deviceStore <- resourceProvider.provide[DeviceStore.View.Self](currentUser)
                      devices <- deviceStore.list(currentUser).map(_.values.map(_.id).toSeq)
                      entryStore <- resourceProvider.provide[DatasetEntryStore.Manage.Self](currentUser)
                      result <- entryStore.delete(devices, entryId)
                    } yield {
                      result
                    }
                  ) { deleted =>
                    if (deleted) {
                      log.info("User [{}] successfully deleted entry [{}]", currentUser, entryId)
                    } else {
                      log.warning("User [{}] failed to delete entry [{}]", currentUser, entryId)
                    }

                    complete(DeletedDatasetEntry(existing = deleted))
                  }
                }
              )
          }
        )
      }
    )
}
