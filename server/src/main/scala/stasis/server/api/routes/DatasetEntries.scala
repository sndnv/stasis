package stasis.server.api.routes

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
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      path("for-definition" / JavaUUID) { definitionId =>
        get {
          resource[DatasetEntryStore.View.Privileged] { view =>
            view.list(definitionId).map { entries =>
              log.info(
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
                  log.info("User [{}] successfully retrieved entry [{}]", currentUser, entryId)
                  discardEntity & complete(entry)

                case None =>
                  log.warning("User [{}] failed to retrieve entry [{}]", currentUser, entryId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            resource[DatasetEntryStore.Manage.Privileged] { manage =>
              manage.delete(entryId).map { deleted =>
                if (deleted) {
                  log.info("User [{}] successfully deleted entry [{}]", currentUser, entryId)
                } else {
                  log.warning("User [{}] failed to delete entry [{}]", currentUser, entryId)
                }

                discardEntity & complete(DeletedDatasetEntry(existing = deleted))
              }
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
                  resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                    deviceView
                      .list(currentUser)
                      .flatMap(devices => entryView.list(devices.keys.toSeq, definitionId))
                      .map { entries =>
                        log.info(
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
                            log.info("User [{}] successfully created entry [{}]", currentUser, entry.id)
                            complete(CreatedDatasetEntry(entry.id))
                          }
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
                  resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] {
                    (deviceView, entryView) =>
                      deviceView
                        .list(currentUser)
                        .flatMap(devices => entryView.get(devices.keys.toSeq, entryId))
                        .map {
                          case Some(entry) =>
                            log.info("User [{}] successfully retrieved entry [{}]", currentUser, entryId)
                            discardEntity & complete(entry)

                          case None =>
                            log.warning("User [{}] failed to retrieve entry [{}]", currentUser, entryId)
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
                          log.info("User [{}] successfully deleted entry [{}]", currentUser, entryId)
                        } else {
                          log.warning("User [{}] failed to delete entry [{}]", currentUser, entryId)
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
