package stasis.server.api.routes

import java.time.Instant

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.server.events.Events.{DatasetEntries => Events}
import stasis.server.persistence.datasets.DatasetEntryStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.DeletedDatasetEntry

class DatasetEntries()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import io.github.sndnv.layers.api.Matchers._

  import stasis.shared.api.Formats._

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
              discardEntity & complete(entries)
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
          pathPrefix("for-definition" / JavaUUID) { definitionId =>
            concat(
              pathEndOrSingleSlash {
                concat(
                  get {
                    resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                      deviceView
                        .list(currentUser)
                        .flatMap(devices => entryView.list(devices.map(_.id), definitionId))
                        .map { entries =>
                          log.debugN(
                            "User [{}] successfully retrieved [{}] entries for definition [{}]",
                            currentUser,
                            entries.size,
                            definitionId
                          )
                          discardEntity & complete(entries)
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
                            .flatMap(devices => entryManage.create(devices.map(_.id), entry))
                            .map { _ =>
                              log.debugN("User [{}] successfully created entry [{}]", currentUser, entry.id)

                              Events.DatasetEntryCreated.recordWithAttributes(
                                Events.Attributes.Device.withValue(value = entry.device)
                              )

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
                  parameter("until".as[Instant].?) { until =>
                    resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                      deviceView
                        .list(currentUser)
                        .flatMap(devices => entryView.latest(devices.map(_.id), definitionId, until))
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
          path(JavaUUID) { entryId =>
            concat(
              get {
                resources[DeviceStore.View.Self, DatasetEntryStore.View.Self] { (deviceView, entryView) =>
                  deviceView
                    .list(currentUser)
                    .flatMap(devices => entryView.get(devices.map(_.id), entryId))
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
                    .flatMap(devices => entryManage.delete(devices.map(_.id), entryId))
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

object DatasetEntries {
  def apply()(implicit ctx: RoutesContext): DatasetEntries = new DatasetEntries()
}
