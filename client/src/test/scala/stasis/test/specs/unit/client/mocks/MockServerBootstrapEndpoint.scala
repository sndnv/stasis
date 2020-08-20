package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.{Materializer, SystemMaterializer}
import org.slf4j.{Logger, LoggerFactory}
import stasis.shared.model.devices.DeviceBootstrapParameters

import scala.concurrent.{ExecutionContextExecutor, Future}

class MockServerBootstrapEndpoint(
  expectedCode: String,
  providedParams: DeviceBootstrapParameters
)(implicit system: ActorSystem[SpawnProtocol.Command]) {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private val bootstrapExecuted: AtomicInteger = new AtomicInteger(0)

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val ec: ExecutionContextExecutor = system.executionContext

  private val http = Http()(system.classicSystem)

  private val routes: Route =
    (extractMethod & extractUri & extractRequest) { (method, uri, request) =>
      extractCredentials {
        case Some(OAuth2BearerToken(`expectedCode`)) =>
          pathPrefix("devices" / "execute") {
            put {
              log.infoN("Successfully executed bootstrap for device [{}]", providedParams.serverApi.device)
              val _ = bootstrapExecuted.incrementAndGet()
              complete(providedParams)
            }
          }

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

  def bootstrapExecutedCount(): Int =
    bootstrapExecuted.get()

  def start(
    port: Int,
    context: Option[HttpsConnectionContext] = None
  ): Future[Http.ServerBinding] = {
    implicit val untyped: akka.actor.ActorSystem = system.classicSystem
    implicit val mat: Materializer = SystemMaterializer(system).materializer

    http.bindAndHandle(
      handler = routes,
      interface = "localhost",
      port = port,
      connectionContext = context.getOrElse(ConnectionContext.noEncryption())
    )
  }
}
