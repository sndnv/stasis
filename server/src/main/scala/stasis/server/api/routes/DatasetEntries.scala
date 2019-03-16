package stasis.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests.CreateDatasetEntry
import stasis.server.api.responses.{CreatedDatasetEntry, DeletedDatasetEntry}
import stasis.server.model.datasets.DatasetEntryStore
import stasis.server.model.devices.DeviceStore

object DatasetEntries extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  def apply()(implicit ctx: RoutesContext): Route =
    concat(
      path("for-definition" / JavaUUID) { definitionId =>
        get {
          resource[DatasetEntryStore.View.Privileged] { view =>
            view.list(definitionId).map { entries =>
              log.info(
                "User [{}] successfully retrieved [{}] entries for definition [{}]",
                ctx.user,
                entries.size,
                definitionId
              )
              complete(entries.values)
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
                  log.info("User [{}] successfully retrieved entry [{}]", ctx.user, entryId)
                  complete(entry)

                case None =>
                  log.warning("User [{}] failed to retrieve entry [{}]", ctx.user, entryId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            resource[DatasetEntryStore.Manage.Privileged] { manage =>
              manage.delete(entryId).map { deleted =>
                if (deleted) {
                  log.info("User [{}] successfully deleted entry [{}]", ctx.user, entryId)
                } else {
                  log.warning("User [{}] failed to delete entry [{}]", ctx.user, entryId)
                }

                complete(DeletedDatasetEntry(existing = deleted))
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
                      .list(ctx.user)
                      .flatMap(devices => entryView.list(devices.keys.toSeq, definitionId))
                      .map { entries =>
                        log.info(
                          "User [{}] successfully retrieved [{}] entries for definition [{}]",
                          ctx.user,
                          entries.size,
                          definitionId
                        )
                        complete(entries.values)
                      }
                  }
                },
                post {
                  entity(as[CreateDatasetEntry]) {
                    case createRequest if createRequest.definition == definitionId =>
                      resources[DeviceStore.View.Self, DatasetEntryStore.Manage.Self] { (deviceView, entryManage) =>
                        val entry = createRequest.toEntry
                        deviceView
                          .list(ctx.user)
                          .flatMap(devices => entryManage.create(devices.keys.toSeq, entry))
                          .map { _ =>
                            log.info("User [{}] successfully created entry [{}]", ctx.user, entry.id)
                            complete(CreatedDatasetEntry(entry.id))
                          }
                      }

                    case createRequest =>
                      log.warning(
                        "User [{}] attempted to create entry for definition [{}] but definition [{}] expected",
                        ctx.user,
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
                  resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                    deviceView
                      .list(ctx.user)
                      .flatMap(devices => entryView.get(devices.keys.toSeq, entryId))
                      .map {
                        case Some(entry) =>
                          log.info("User [{}] successfully retrieved entry [{}]", ctx.user, entryId)
                          complete(entry)

                        case None =>
                          log.warning("User [{}] failed to retrieve entry [{}]", ctx.user, entryId)
                          complete(StatusCodes.NotFound)
                      }
                  }
                },
                delete {
                  resources[DeviceStore.View.Self, DatasetEntryStore.Manage.Self] { (deviceView, entryManage) =>
                    deviceView
                      .list(ctx.user)
                      .flatMap(devices => entryManage.delete(devices.keys.toSeq, entryId))
                      .map { deleted =>
                        if (deleted) {
                          log.info("User [{}] successfully deleted entry [{}]", ctx.user, entryId)
                        } else {
                          log.warning("User [{}] failed to delete entry [{}]", ctx.user, entryId)
                        }

                        complete(DeletedDatasetEntry(existing = deleted))
                      }
                  }
                }
              )
          }
        )
      }
    )
}
