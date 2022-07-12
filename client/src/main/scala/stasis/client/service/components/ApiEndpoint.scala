package stasis.client.service.components

import akka.http.scaladsl.Http.ServerBinding
import akka.util.ByteString
import org.slf4j.LoggerFactory
import stasis.client.api.http
import stasis.client.api.http.HttpApiEndpoint
import stasis.client.security.DefaultFrontendAuthenticator
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.core.security.tls.EndpointContext

import scala.concurrent.Future

trait ApiEndpoint {
  def api: ApiEndpoint.Startable
}

object ApiEndpoint {
  trait Startable {
    def start(): Future[ServerBinding]
  }

  implicit val tokenFileToByteString: String => ByteString =
    (content: String) => ByteString.fromString(content)

  def apply(base: Base, apiClients: ApiClients, ops: Ops): Future[ApiEndpoint] = {
    import apiClients._
    import base._
    import ops._

    for {
      tokenSize <- rawConfig.getInt("api.authentication.token-size").future
      frontendAuthenticator = DefaultFrontendAuthenticator(tokenSize)
      _ = log.debug("Creating API token file [{}]...", Files.ApiToken)
      tokenFile <-
        directory
          .pushFile(
            file = Files.ApiToken,
            content = frontendAuthenticator.token
          )
          .transformFailureTo(ServiceStartupFailure.file)
    } yield {
      implicit val context: http.Context = http.Context(
        api = clients.api,
        executor = executor,
        scheduler = scheduler,
        trackers = trackers.views,
        search = search,
        terminateService = () => {
          val _ = akka.pattern.after(
            duration = terminationDelay,
            using = system.classicSystem.scheduler
          ) { Future.successful(base.terminateService()) }
        },
        log = LoggerFactory.getLogger(this.getClass.getName)
      )

      log.debug("Successfully created API token file [{}]", tokenFile)

      new ApiEndpoint {
        override def api: Startable =
          rawConfig.getString("api.type").toLowerCase match {
            case "http" =>
              val api = HttpApiEndpoint(authenticator = frontendAuthenticator)
              val apiInterface = rawConfig.getString("api.http.interface")
              val apiPort = rawConfig.getInt("api.http.port")

              () => {
                log.info("Client HTTP API starting on [{}:{}]...", apiInterface, apiPort)

                api
                  .start(
                    interface = apiInterface,
                    port = apiPort,
                    context = EndpointContext(rawConfig.getConfig("api.http.context"))
                  )
                  .transformFailureTo(ServiceStartupFailure.api)
              }
          }
      }
    }
  }
}
