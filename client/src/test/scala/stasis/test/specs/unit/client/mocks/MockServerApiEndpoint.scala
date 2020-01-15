package stasis.test.specs.unit.client.mocks

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.shared.api.requests.{CreateDatasetDefinition, CreateDatasetEntry}
import stasis.shared.api.responses.{CreatedDatasetDefinition, CreatedDatasetEntry, Ping}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.shared.model.Generators

class MockServerApiEndpoint(
  expectedCredentials: BasicHttpCredentials,
  definitionsWithoutEntries: Seq[DatasetDefinition.Id] = Seq.empty
)(implicit system: ActorSystem, timeout: Timeout) {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val entriesStore: MemoryBackend[DatasetEntry.Id, DatasetEntry] =
    MemoryBackend.untyped[DatasetDefinition.Id, DatasetEntry](
      s"mock-server-api-entries-store-${java.util.UUID.randomUUID()}"
    )

  private val definitionsStore: MemoryBackend[DatasetDefinition.Id, DatasetDefinition] =
    MemoryBackend.untyped[DatasetDefinition.Id, DatasetDefinition](
      s"mock-server-api-definitions-store-${java.util.UUID.randomUUID()}"
    )

  private val definitions: Route = {
    pathPrefix("definitions") {
      concat(
        pathPrefix("own") {
          concat(
            pathEndOrSingleSlash {
              concat(
                get {
                  val definitions: Seq[DatasetDefinition] = Seq(
                    Generators.generateDefinition,
                    Generators.generateDefinition
                  )

                  log.info("Successfully retrieved definitions [{}]", definitions.map(_.id).mkString(", "))

                  complete(definitions)
                },
                post {
                  entity(as[CreateDatasetDefinition]) {
                    createRequest =>
                      val definition = createRequest.toDefinition
                      onComplete(definitionsStore.put(definition.id, definition)) {
                        case Success(_) =>
                          log.info("Successfully created definition [{}]", definition.id)
                          complete(CreatedDatasetDefinition(definition.id))

                        case Failure(e) =>
                          log.error(e, "Failed to create definition [{}]: [{}]", definition.id, e.getMessage)
                          complete(StatusCodes.InternalServerError)
                      }
                  }
                }
              )
            },
            path(JavaUUID) { definitionId =>
              get {
                val definition: DatasetDefinition = Generators.generateDefinition.copy(id = definitionId)
                log.info("Successfully retrieved definition [{}]", definition.id)
                complete(definition)
              }
            }
          )
        }
      )
    }
  }

  private val entries: Route = {
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

                    log.info("Successfully retrieved entries [{}]", entries.map(_.id).mkString(", "))

                    complete(entries)

                  },
                  post {
                    entity(as[CreateDatasetEntry]) {
                      case createRequest if createRequest.definition == definitionId =>
                        val entry = createRequest.toEntry
                        onComplete(entriesStore.put(entry.id, entry)) {
                          case Success(_) =>
                            log.info("Successfully created entry [{}]", entry.id)
                            complete(CreatedDatasetEntry(entry.id))

                          case Failure(e) =>
                            log.error(e, "Failed to create entry [{}]: [{}]", entry.id, e.getMessage)
                            complete(StatusCodes.InternalServerError)
                        }

                      case createRequest =>
                        log.error(
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
                  log.warning("No entry found for definition [{}]", definitionId)
                  complete(StatusCodes.NotFound)
                } else {
                  val entry: DatasetEntry = Generators.generateEntry.copy(definition = definitionId)
                  log.info("Successfully retrieved latest entry [{}]", entry)
                  complete(entry)
                }
              }
            )
          },
          path(JavaUUID) { entryId =>
            get {
              val entry: DatasetEntry = Generators.generateEntry.copy(id = entryId)
              log.info("Successfully retrieved entry [{}]", entry.id)
              complete(entry)
            }
          }
        )
      }
    }
  }

  private val schedules: Route = {
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
  }

  private val users: Route =
    path("self") {
      val user: User = Generators.generateUser
      complete(user)
    }

  private val devices: Route =
    path("own" / JavaUUID) { deviceId =>
      val device: Device = Generators.generateDevice.copy(id = deviceId)
      complete(device)
    }

  private val service: Route =
    path("ping") {
      complete(Ping())
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
            pathPrefix("service") { service }
          )

        case _ =>
          val _ = request.discardEntityBytes()

          log.warning(
            "Rejecting [{}] request for [{}] with no/invalid credentials from [{}]",
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

  def start(port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(handler = routes, interface = "localhost", port = port)
}
