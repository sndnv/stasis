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
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class MockServerApiEndpoint(
  expectedCredentials: BasicHttpCredentials
)(implicit system: ActorSystem, timeout: Timeout) {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val store: MemoryBackend[DatasetEntry.Id, DatasetEntry] =
    MemoryBackend.untyped[DatasetDefinition.Id, DatasetEntry](
      s"mock-server-api-store-${java.util.UUID.randomUUID()}"
    )

  private val routes: Route =
    (extractMethod & extractUri & extractRequest) { (method, uri, request) =>
      extractCredentials {
        case Some(`expectedCredentials`) =>
          pathPrefix("own") {
            path("for-definition" / JavaUUID) { definitionId =>
              post {
                entity(as[CreateDatasetEntry]) {
                  case createRequest if createRequest.definition == definitionId =>
                    val entry = createRequest.toEntry
                    onComplete(store.put(entry.id, entry)) {
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
            }
          }

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
    store.contains(entry)

  def start(port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(handler = routes, interface = "localhost", port = port)
}
