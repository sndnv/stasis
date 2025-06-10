package stasis.test.specs.unit.client.mocks

import java.time.Instant

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.commands.proto.Command
import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.security.tls.EndpointContext
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.api.requests.CreateAnalyticsEntry
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.api.responses.CreatedAnalyticsEntry
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.Ping
import stasis.shared.api.responses.UpdatedUserSalt
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.shared.model.Generators

class MockServerApiEndpoint(
  expectedCredentials: HttpCredentials,
  expectedDeviceKey: Option[ByteString] = None,
  definitionsWithoutEntries: Seq[DatasetDefinition.Id] = Seq.empty,
  withEntries: Option[Seq[DatasetEntry]] = None,
  withDefinitions: Option[Seq[DatasetDefinition]] = None,
  withCommands: Option[Seq[Command]] = None
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout) {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import system.executionContext

  import stasis.shared.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val entriesStore: MemoryStore[DatasetEntry.Id, DatasetEntry] =
    MemoryStore[DatasetDefinition.Id, DatasetEntry](
      s"mock-server-api-entries-store-${java.util.UUID.randomUUID()}"
    )

  private val definitionsStore: MemoryStore[DatasetDefinition.Id, DatasetDefinition] =
    MemoryStore[DatasetDefinition.Id, DatasetDefinition](
      s"mock-server-api-definitions-store-${java.util.UUID.randomUUID()}"
    )

  private val keyStore: MemoryStore[Device.Id, DeviceKey] =
    MemoryStore[Device.Id, DeviceKey](
      s"mock-server-api-device-key-store-${java.util.UUID.randomUUID()}"
    )

  private val analyticsStore: MemoryStore[StoredAnalyticsEntry.Id, StoredAnalyticsEntry] =
    MemoryStore[StoredAnalyticsEntry.Id, StoredAnalyticsEntry](
      s"mock-server-api-analytics-store-${java.util.UUID.randomUUID()}"
    )

  private val definitions: Route =
    pathPrefix("definitions") {
      concat(
        pathPrefix("own") {
          concat(
            pathEndOrSingleSlash {
              concat(
                get {
                  val definitions: Seq[DatasetDefinition] = withDefinitions match {
                    case Some(definitions) =>
                      definitions

                    case None =>
                      Seq(
                        Generators.generateDefinition,
                        Generators.generateDefinition
                      )
                  }

                  log.infoN("Successfully retrieved definitions [{}]", definitions.map(_.id).mkString(", "))

                  complete(definitions)
                },
                post {
                  entity(as[CreateDatasetDefinition]) { createRequest =>
                    val definition = createRequest.toDefinition
                    onComplete(definitionsStore.put(definition.id, definition)) {
                      case Success(_) =>
                        log.infoN("Successfully created definition [{}]", definition.id)
                        complete(CreatedDatasetDefinition(definition.id))

                      case Failure(e) =>
                        log.errorN(
                          "Failed to create definition [{}]: [{} - {}]",
                          definition.id,
                          e.getClass.getSimpleName,
                          e.getMessage
                        )
                        complete(StatusCodes.InternalServerError)
                    }
                  }
                }
              )
            },
            path(JavaUUID) { definitionId =>
              concat(
                get {
                  val definition: Option[DatasetDefinition] = withDefinitions match {
                    case Some(definitions) => definitions.find(_.id == definitionId)
                    case None              => Some(Generators.generateDefinition.copy(id = definitionId))
                  }

                  definition match {
                    case Some(definition) =>
                      log.infoN("Successfully retrieved definition [{}]", definition.id)
                      complete(definition)

                    case None =>
                      log.warnN("Definition [{}] not found", definitionId)
                      complete(StatusCodes.NotFound)
                  }
                },
                put {
                  entity(as[UpdateDatasetDefinition]) { updateRequest =>
                    withDefinitions.flatMap(_.find(_.id == definitionId)) match {
                      case Some(existing) =>
                        val updated = updateRequest.toUpdatedDefinition(existing)
                        onSuccess(definitionsStore.put(updated.id, updated)) { _ =>
                          log.infoN("Successfully updated definition [{}]", updated.id)
                          complete(StatusCodes.OK)
                        }

                      case None =>
                        log.warnN("Definition [{}] not found", definitionId)
                        complete(StatusCodes.NotFound)
                    }
                  }
                },
                delete {
                  withDefinitions.flatMap(_.find(_.id == definitionId)) match {
                    case Some(existing) =>
                      log.infoN("Successfully removed existing definition [{}]", existing.id)
                      complete(StatusCodes.OK)

                    case None =>
                      log.warnN("Definition [{}] not found", definitionId)
                      complete(StatusCodes.NotFound)
                  }
                }
              )
            }
          )
        }
      )
    }

  private val entries: Route =
    pathPrefix("entries") {
      pathPrefix("own") {
        concat(
          pathPrefix("for-definition" / JavaUUID) { definitionId =>
            concat(
              pathEndOrSingleSlash {
                concat(
                  get {
                    val entries: Seq[DatasetEntry] = if (definitionsWithoutEntries.contains(definitionId)) {
                      Seq.empty
                    } else {
                      Seq(
                        Generators.generateEntry,
                        Generators.generateEntry,
                        Generators.generateEntry
                      ).map(_.copy(definition = definitionId))
                    }

                    log.infoN("Successfully retrieved entries [{}]", entries.map(_.id).mkString(", "))

                    complete(entries)

                  },
                  post {
                    entity(as[CreateDatasetEntry]) {
                      case createRequest if createRequest.definition == definitionId =>
                        val entry = createRequest.toEntry
                        onComplete(entriesStore.put(entry.id, entry)) {
                          case Success(_) =>
                            log.infoN("Successfully created entry [{}]", entry.id)
                            complete(CreatedDatasetEntry(entry.id))

                          case Failure(e) =>
                            log.errorN("Failed to create entry [{}]: [{}]", entry.id, e.getMessage, e)
                            complete(StatusCodes.InternalServerError)
                        }

                      case createRequest =>
                        log.errorN(
                          "Attempted to create entry for definition [{}] but definition [{}] expected",
                          createRequest.definition,
                          definitionId
                        )
                        complete(StatusCodes.BadRequest)
                    }
                  }
                )
              },
              path("latest") {
                if (definitionsWithoutEntries.contains(definitionId)) {
                  log.warnN("No entry found for definition [{}]", definitionId)
                  complete(StatusCodes.NotFound)
                } else {
                  val entry: DatasetEntry = Generators.generateEntry.copy(definition = definitionId)
                  log.infoN("Successfully retrieved latest entry [{}]", entry)
                  complete(entry)
                }
              }
            )
          },
          path(JavaUUID) { entryId =>
            concat(
              get {
                val entry: DatasetEntry = Generators.generateEntry.copy(id = entryId)
                log.infoN("Successfully retrieved entry [{}]", entry.id)
                complete(entry)
              },
              delete {
                withEntries.flatMap(_.find(_.id == entryId)) match {
                  case Some(existing) =>
                    log.infoN("Successfully removed existing entry [{}]", existing.id)
                    complete(StatusCodes.OK)

                  case None =>
                    log.warnN("Entry [{}] not found", entryId)
                    complete(StatusCodes.NotFound)
                }
              }
            )
          }
        )
      }
    }

  private val schedules: Route =
    pathPrefix("public") {
      concat(
        pathEndOrSingleSlash {
          val schedules: Seq[Schedule] = Seq(
            Generators.generateSchedule,
            Generators.generateSchedule,
            Generators.generateSchedule
          ).map(_.copy(isPublic = true))

          log.info("Successfully retrieved schedules [{}]", schedules.map(_.id).mkString(", "))

          complete(schedules)
        },
        path(JavaUUID) { scheduleId =>
          val schedule: Schedule = Generators.generateSchedule.copy(id = scheduleId, isPublic = true)
          log.info("Successfully retrieved schedule [{}]", schedule)
          complete(schedule)
        }
      )
    }

  private val users: Route =
    pathPrefix("self") {
      concat(
        pathEndOrSingleSlash {
          val user: User = Generators.generateUser
          complete(user)
        },
        path("salt") {
          put {
            complete(UpdatedUserSalt(salt = "updated-salt"))
          }
        },
        path("password") {
          put {
            complete(StatusCodes.OK)
          }
        }
      )
    }

  private val devices: Route =
    pathPrefix("own" / JavaUUID) { deviceId =>
      concat(
        pathEndOrSingleSlash {
          val device: Device = Generators.generateDevice.copy(id = deviceId)
          complete(device)
        },
        path("key") {
          concat(
            head {
              expectedDeviceKey match {
                case Some(_) => complete(HttpEntity(ContentTypes.`application/octet-stream`, Source.empty))
                case None =>
                  onSuccess(keyStore.get(deviceId)) {
                    case Some(_) => complete(HttpEntity(ContentTypes.`application/octet-stream`, Source.empty))
                    case None    => complete(StatusCodes.NotFound)
                  }
              }
            },
            get {
              expectedDeviceKey match {
                case Some(key) => complete(HttpEntity(ContentTypes.`application/octet-stream`, Source.single(key)))
                case None =>
                  onSuccess(keyStore.get(deviceId)) {
                    case Some(key) => complete(HttpEntity(ContentTypes.`application/octet-stream`, Source.single(key.value)))
                    case None      => complete(StatusCodes.NotFound)
                  }
              }
            },
            put {
              extractDataBytes { source =>
                val result = for {
                  key <- source.runFold(ByteString.empty)(_ concat _)
                  _ <- keyStore.put(
                    key = deviceId,
                    value = DeviceKey(
                      value = key,
                      owner = Generators.generateUser.id,
                      device = deviceId,
                      created = Instant.now()
                    )
                  )
                } yield Done

                onSuccess(result) { _ =>
                  complete(StatusCodes.OK)
                }
              }
            }
          )
        },
        path("commands") {
          get {
            parameter("last_sequence_id".as[Long].?) { _ =>
              val commands = withCommands match {
                case Some(commands) =>
                  commands

                case None =>
                  Seq(
                    stasis.test.specs.unit.core.persistence.Generators.generateCommand,
                    stasis.test.specs.unit.core.persistence.Generators.generateCommand
                  ).zipWithIndex.map { case (c, i) => c.copy(sequenceId = i.toLong) }
              }

              complete(commands)
            }
          }
        }
      )
    }

  private val service: Route =
    path("ping") {
      val ping = Ping()
      log.info("Responding to ping request with ping ID [{}]", ping.id)
      complete(ping)
    }

  private val analytics: Route =
    pathEndOrSingleSlash {
      post {
        entity(as[CreateAnalyticsEntry]) { request =>
          val entry = request.toStoredAnalyticsEntry
          onComplete(analyticsStore.put(entry.id, entry)) {
            case Success(_) =>
              log.infoN("Successfully created analytics entry [{}]", entry.id)
              complete(CreatedAnalyticsEntry(entry.id))

            case Failure(e) =>
              log.errorN(
                "Failed to create analytics entry [{}]: [{} - {}]",
                entry.id,
                e.getClass.getSimpleName,
                e.getMessage
              )
              complete(StatusCodes.InternalServerError)
          }
        }
      }
    }

  private val discovery: Route =
    path("provide") {
      post {
        import stasis.core.api.Formats._

        entity(as[ServiceDiscoveryRequest]) { request =>
          val result: ServiceDiscoveryResult = ServiceDiscoveryResult.KeepExisting

          log.info("Responding to discovery request [{}] with [{}]", request.id, result.asString)

          complete(result)
        }
      }
    }

  private val routes: Route =
    (extractMethod & extractUri & extractRequest) { (method, uri, request) =>
      extractCredentials {
        case Some(`expectedCredentials`) =>
          concat(
            pathPrefix("datasets") { concat(definitions, entries) },
            pathPrefix("users") { users },
            pathPrefix("devices") { devices },
            pathPrefix("schedules") { schedules },
            pathPrefix("service") { service },
            pathPrefix("analytics") { analytics },
            pathPrefix("discovery") { discovery }
          )

        case _ =>
          val _ = request.discardEntityBytes()

          log.warnN(
            "Rejecting [{}] request for [{}] with no/invalid credentials",
            method.value,
            uri
          )

          complete(StatusCodes.Unauthorized)
      }
    }

  def entryExists(entry: DatasetEntry.Id): Future[Boolean] =
    entriesStore.contains(entry)

  def definitionExists(definition: DatasetDefinition.Id): Future[Boolean] =
    definitionsStore.contains(definition)

  def deviceKeyExists(forDevice: Device.Id): Future[Boolean] =
    keyStore.contains(forDevice)

  def deviceKey(forDevice: Device.Id): Future[Option[ByteString]] =
    keyStore.get(forDevice).map(_.map(_.value))

  def start(port: Int, context: Option[EndpointContext] = None): Future[Http.ServerBinding] = {
    val server = {
      val builder = Http().newServerAt(interface = "localhost", port = port)

      context match {
        case Some(httpsContext) => builder.enableHttps(httpsContext.connection)
        case None               => builder
      }
    }

    server.bindFlow(handlerFlow = pathPrefix("v1") { routes })
  }
}
