package stasis.client.service.components

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.util.ByteString
import org.slf4j.LoggerFactory

import stasis.client.api.Context
import stasis.client.api.http.HttpApiEndpoint
import stasis.client.security.DefaultFrontendAuthenticator
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.layers.security.tls.EndpointContext

trait ApiEndpoint {
  def context: Context
  def api: ApiEndpoint.Startable
}

object ApiEndpoint {
  trait Startable {
    def start(): Future[ServerBinding]
  }

  implicit val tokenFileToByteString: String => ByteString =
    (content: String) => ByteString.fromString(content)

  def apply(base: Base, tracking: Tracking, apiClients: ApiClients, ops: Ops, secrets: Secrets): Future[ApiEndpoint] = {
    import apiClients._
    import base._
    import ops._
    import tracking._

    for {
      tokenSize <- rawConfig.getInt("api.authentication.token-size").future
      frontendAuthenticator = DefaultFrontendAuthenticator(tokenSize)
      _ = log.debug("Creating API token file [{}]...", Files.ApiToken)
      tokenFile <- directory
        .pushFile(file = Files.ApiToken, content = frontendAuthenticator.token)
        .transformFailureTo(ServiceStartupFailure.file)
    } yield {
      implicit val ctx: Context = Context(
        api = clients.api,
        executor = executor,
        scheduler = scheduler,
        trackers = trackers.views,
        search = search,
        handlers = Context.Handlers(
          terminateService = () => {
            val _ = org.apache.pekko.pattern.after(
              duration = terminationDelay,
              using = system.classicSystem.scheduler
            ) { Future.successful(base.terminateService()) }
          },
          verifyUserPassword = secrets.verifyUserPassword,
          updateUserCredentials = secrets.updateUserCredentials(clients.api, _, _),
          reEncryptDeviceSecret = secrets.reEncryptDeviceSecret(clients.api, _)
        ),
        secretsConfig = secrets.config,
        log = LoggerFactory.getLogger(this.getClass.getName)
      )

      log.debug("Successfully created API token file [{}]", tokenFile)

      new ApiEndpoint {
        override val context: Context = ctx

        override val api: Startable =
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
