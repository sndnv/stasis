package stasis.server.api.routes

import java.time.Instant

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.datasets.DatasetEntryStore
import stasis.server.model.devices.DeviceStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.{CreatedDatasetEntry, DeletedDatasetEntry}

class DatasetEntries()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Matchers._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      path("for-definition" / JavaUUID) { definitionId =>
        get {
          resource[DatasetEntryStore.View.Privileged] { view =>
            view.list(definitionId).map { entries =>
              log.debugN(
                "User [{}] successfully retrieved [{}] entries for definition [{}]",
                currentUser,
                entries.size,
                definitionId
              )
              discardEntity & complete(entries.values)
            }
          }
        }
      },
      path(JavaUUID) { entryId =>
        concat(
          get {
            resource[DatasetEntryStore.View.Privileged] { view =>
              view.get(entryId).map {
                case Some(entry) =>
                  log.debugN("User [{}] successfully retrieved entry [{}]", currentUser, entryId)
                  discardEntity & complete(entry)

                case None =>
                  log.warnN("User [{}] failed to retrieve entry [{}]", currentUser, entryId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            resource[DatasetEntryStore.Manage.Privileged] { manage =>
              manage.delete(entryId).map { deleted =>
                if (deleted) {
                  log.debugN("User [{}] successfully deleted entry [{}]", currentUser, entryId)
                } else {
                  log.warnN("User [{}] failed to delete entry [{}]", currentUser, entryId)
                }

                discardEntity & complete(DeletedDatasetEntry(existing = deleted))
              }
            }
          }
        )
      },
      pathPrefix("own") {
        concat(
          pathPrefix("for-definition" / JavaUUID) {
            definitionId =>
              concat(
                pathEndOrSingleSlash {
                  concat(
                    get {
                      resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                        deviceView
                          .list(currentUser)
                          .flatMap(devices => entryView.list(devices.keys.toSeq, definitionId))
                          .map { entries =>
                            log.debugN(
                              "User [{}] successfully retrieved [{}] entries for definition [{}]",
                              currentUser,
                              entries.size,
                              definitionId
                            )
                            discardEntity & complete(entries.values)
                          }
                      }
                    },
                    post {
                      entity(as[CreateDatasetEntry]) {
                        case createRequest if createRequest.definition == definitionId =>
                          resources[DeviceStore.View.Self, DatasetEntryStore.Manage.Self] { (deviceView, entryManage) =>
                            val entry = createRequest.toEntry
                            deviceView
                              .list(currentUser)
                              .flatMap(devices => entryManage.create(devices.keys.toSeq, entry))
                              .map { _ =>
                                log.debugN("User [{}] successfully created entry [{}]", currentUser, entry.id)
                                complete(CreatedDatasetEntry(entry.id))
                              }
                          }

                        case createRequest =>
                          log.warnN(
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
                path("latest") {
                  get {
                    parameter("until".as[Instant].?) {
                      until =>
                        resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] {
                          (deviceView, entryView) =>
                            deviceView
                              .list(currentUser)
                              .flatMap(devices => entryView.latest(devices.keys.toSeq, definitionId, until))
                              .map {
                                case Some(entry) =>
                                  log.debugN(
                                    "User [{}] successfully retrieved latest entry [{}] for definition [{}]",
                                    currentUser,
                                    entry.id,
                                    definitionId
                                  )
                                  discardEntity & complete(entry)

                                case None =>
                                  log.warnN(
                                    "User [{}] failed to retrieve latest entry for definition [{}]",
                                    currentUser,
                                    definitionId
                                  )
                                  discardEntity & complete(StatusCodes.NotFound)
                              }
                        }
                    }
                  }
                }
              )
          },
          path(JavaUUID) {
            entryId =>
              concat(
                get {
                  resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                    deviceView
                      .list(currentUser)
                      .flatMap(devices => entryView.get(devices.keys.toSeq, entryId))
                      .map {
                        case Some(entry) =>
                          log.debugN("User [{}] successfully retrieved entry [{}]", currentUser, entryId)
                          discardEntity & complete(entry)

                        case None =>
                          log.warnN("User [{}] failed to retrieve entry [{}]", currentUser, entryId)
                          discardEntity & complete(StatusCodes.NotFound)
                      }
                  }
                },
                delete {
                  resources[DeviceStore.View.Self, DatasetEntryStore.Manage.Self] { (deviceView, entryManage) =>
                    deviceView
                      .list(currentUser)
                      .flatMap(devices => entryManage.delete(devices.keys.toSeq, entryId))
                      .map { deleted =>
                        if (deleted) {
                          log.debugN("User [{}] successfully deleted entry [{}]", currentUser, entryId)
                        } else {
                          log.warnN("User [{}] failed to delete entry [{}]", currentUser, entryId)
                        }

                        discardEntity & complete(DeletedDatasetEntry(existing = deleted))
                      }
                  }
                }
              )
          }
        )
      }
    )
}
