package stasis.client.service.components

import akka.event.Logging
import akka.util.ByteString
import stasis.client.api.http
import stasis.client.api.http.HttpApiEndpoint
import stasis.client.security.DefaultFrontendAuthenticator
import stasis.core.security.tls.EndpointContext

import scala.concurrent.Future
import scala.concurrent.duration._

trait ApiEndpoint {
  def api: ApiEndpoint.Startable
}

object ApiEndpoint {
  trait Startable {
    def start(): Unit
  }

  implicit val tokenFileToByteString: String => ByteString =
    (content: String) => ByteString.fromString(content)

  def apply(base: Base, apiClients: ApiClients, ops: Ops): Future[ApiEndpoint] = {
    import apiClients._
    import base._
    import ops._

    for {
      terminationDelay <- rawConfig.getDuration("api.termination-delay").toMillis.millis.future
      tokenSize <- rawConfig.getInt("api.authentication.token-size").future
      frontendAuthenticator = DefaultFrontendAuthenticator(tokenSize)
      _ = log.debug("Creating API token file [{}]...", Files.ApiToken)
      tokenFile <- directory.pushFile(
        file = Files.ApiToken,
        content = frontendAuthenticator.token
      )
    } yield {
      implicit val context: http.Context = http.Context(
        api = clients.api,
        executor = executor,
        scheduler = scheduler,
        tracker = tracker,
        search = search,
        terminateService = () => {
          val _ = akka.pattern.after(
            duration = terminationDelay,
            using = system.scheduler
          ) { Future.successful(base.terminateService()) }
        },
        log = Logging(untyped, this.getClass.getName)
      )

      log.debug("Successfully created API token file [{}]", tokenFile)

      new ApiEndpoint {
        override def api: Startable =
          rawConfig.getString("api.type").toLowerCase match {
            case "http" =>
              val api = new HttpApiEndpoint(authenticator = frontendAuthenticator)
              val apiInterface = rawConfig.getString("api.http.interface")
              val apiPort = rawConfig.getInt("api.http.port")

              () =>
                {
                  log.info("Client HTTP API starting on [{}:{}]...", apiInterface, apiPort)

                  val _ = api.start(
                    interface = apiInterface,
                    port = apiPort,
                    context = EndpointContext.fromConfig(rawConfig.getConfig("api.http.context"))
                  )
                }
          }
      }
    }
  }
}
