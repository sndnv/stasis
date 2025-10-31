package stasis.server.service.components

import scala.concurrent.duration._

import com.typesafe.config.Config
import com.typesafe.{config => typesafe}
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import stasis.server.api.BootstrapEndpoint
import stasis.server.api.routes.DeviceBootstrap
import stasis.server.security.CredentialsManagers
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.BootstrapCodeAuthenticator
import stasis.server.security.authenticators.DefaultBootstrapCodeAuthenticator
import stasis.server.security.devices.DeviceBootstrapCodeGenerator
import stasis.server.security.devices.DeviceClientSecretGenerator
import stasis.server.service.ServerPersistence
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.secrets.SecretsConfig

object BootstrapEndpointComponentLoader
    extends ComponentLoader.Fixed[
      BootstrapEndpoint,
      BootstrapEndpointComponentLoaderContext
    ] {
  override val name: String = "bootstrap"
  override val component: Option[String] = None

  override protected def default(
    config: Config
  )(implicit context: ComponentLoader.Context[BootstrapEndpointComponentLoaderContext]): Component[BootstrapEndpoint] = {
    implicit val (system, _, telemetry) =
      context.value.base.components

    implicit val (authenticators, credentialsManagers, eventCollector, serverPersistence, resourceProvider) =
      context.value.components

    new DefaultBootstrapEndpointComponent(
      config = DefaultBootstrapEndpointComponent.Config(config)
    )
  }

  private[components] class DefaultBootstrapEndpointComponent(
    config: DefaultBootstrapEndpointComponent.Config
  )(implicit
    system: ActorSystem[Nothing],
    telemetryContext: TelemetryContext,
    authenticators: Authenticators,
    credentialsManagers: CredentialsManagers,
    eventCollector: EventCollector,
    serverPersistence: ServerPersistence,
    resourceProvider: ResourceProvider
  ) extends Component[BootstrapEndpoint.Default] {
    import system.executionContext

    override def renderConfig(withPrefix: String): String =
      s"""
         |$withPrefix  api:
         |$withPrefix    interface: ${config.api.interface}
         |$withPrefix    port:      ${config.api.port.toString}
         |$withPrefix    context:
         |$withPrefix      enabled:  ${config.api.context.nonEmpty.toString}
         |$withPrefix      protocol: ${config.api.context.map(_.config.protocol).getOrElse("none")}
         |$withPrefix      keystore: ${config.api.context
          .flatMap(_.config.keyStoreConfig)
          .map(_.storePath)
          .getOrElse("none")}
         |$withPrefix
         |$withPrefix  devices:
         |$withPrefix    code-size:              ${config.devices.codeSize.toString}
         |$withPrefix    code-expiration:        ${config.devices.codeExpiration.toMillis.toString} ms
         |$withPrefix    secret-size:            ${config.devices.secretSize.toString}
         |$withPrefix    parameters:
         |$withPrefix      authentication:
         |$withPrefix        token-endpoint:     ${config.devices.parameters.authentication.tokenEndpoint}
         |$withPrefix        use-query-string:   ${config.devices.parameters.authentication.useQueryString.toString}
         |$withPrefix        scopes-api:         ${config.devices.parameters.authentication.scopes.api}
         |$withPrefix        scopes-core:        ${config.devices.parameters.authentication.scopes.core}
         |$withPrefix      server-api:
         |$withPrefix        url:                ${config.devices.parameters.serverApi.url}
         |$withPrefix        context-enabled:    ${config.devices.parameters.serverApi.context.enabled.toString}
         |$withPrefix        context-protocol:   ${config.devices.parameters.serverApi.context.protocol}
         |$withPrefix      server-core:
         |$withPrefix        address:            ${config.devices.parameters.serverCore.address}
         |$withPrefix        context-enabled:    ${config.devices.parameters.serverCore.context.enabled.toString}
         |$withPrefix        context-protocol:   ${config.devices.parameters.serverCore.context.protocol}
         |$withPrefix      secrets:
         |$withPrefix        derivation:
         |$withPrefix          encryption:
         |$withPrefix            secret-size:    ${config.devices.parameters.secrets.derivation.encryption.secretSize.toString}  bytes
         |$withPrefix            iterations:     ${config.devices.parameters.secrets.derivation.encryption.iterations.toString}
         |$withPrefix            salt-prefix:    ${config.devices.parameters.secrets.derivation.encryption.saltPrefix}
         |$withPrefix          authentication:
         |$withPrefix            enabled:        ${config.devices.parameters.secrets.derivation.authentication.enabled.toString}
         |$withPrefix            secret-size:    ${config.devices.parameters.secrets.derivation.authentication.secretSize.toString}  bytes
         |$withPrefix            iterations:     ${config.devices.parameters.secrets.derivation.authentication.iterations.toString}
         |$withPrefix            salt-prefix:    ${config.devices.parameters.secrets.derivation.authentication.saltPrefix}
         |$withPrefix        encryption:
         |$withPrefix          file:
         |$withPrefix            key-size:       ${config.devices.parameters.secrets.encryption.file.keySize.toString} bytes
         |$withPrefix            iv-size:        ${config.devices.parameters.secrets.encryption.file.ivSize.toString} bytes
         |$withPrefix          metadata:
         |$withPrefix            key-size:       ${config.devices.parameters.secrets.encryption.metadata.keySize.toString} bytes
         |$withPrefix            iv-size:        ${config.devices.parameters.secrets.encryption.metadata.ivSize.toString} bytes
         |$withPrefix          device-secret:
         |$withPrefix            key-size:       ${config.devices.parameters.secrets.encryption.deviceSecret.keySize.toString} bytes
         |$withPrefix            iv-size:        ${config.devices.parameters.secrets.encryption.deviceSecret.ivSize.toString} bytes
         |$withPrefix      additional-config:    ${config.devices.parameters.additionalConfig.fields.nonEmpty.toString}""".stripMargin

    override def component: BootstrapEndpoint.Default = {
      val bootstrapCodeAuthenticator: BootstrapCodeAuthenticator = DefaultBootstrapCodeAuthenticator(
        store = serverPersistence.deviceBootstrapCodes.manage()
      )

      val deviceBootstrapCodeGenerator: DeviceBootstrapCodeGenerator = DeviceBootstrapCodeGenerator(
        codeSize = config.devices.codeSize,
        expiration = config.devices.codeExpiration
      )

      val deviceClientSecretGenerator: DeviceClientSecretGenerator = DeviceClientSecretGenerator(
        secretSize = config.devices.secretSize
      )

      val endpoint = new BootstrapEndpoint.Default(
        resourceProvider = resourceProvider,
        eventCollector = eventCollector,
        userAuthenticator = authenticators.users,
        bootstrapCodeAuthenticator = bootstrapCodeAuthenticator,
        deviceBootstrapContext = DeviceBootstrap.BootstrapContext(
          bootstrapCodeGenerator = deviceBootstrapCodeGenerator,
          clientSecretGenerator = deviceClientSecretGenerator,
          credentialsManager = credentialsManagers.devices,
          deviceParams = config.devices.parameters
        )
      )

      endpoint.start(
        interface = config.api.interface,
        port = config.api.port,
        context = config.api.context
      )

      endpoint
    }
  }

  private[components] object DefaultBootstrapEndpointComponent {
    final case class Config(
      api: Config.Api,
      devices: Config.Devices
    )

    object Config {
      final case class Api(
        interface: String,
        port: Int,
        context: Option[EndpointContext]
      )

      final case class Devices(
        codeSize: Int,
        codeExpiration: FiniteDuration,
        secretSize: Int,
        parameters: DeviceBootstrapParameters
      )

      def apply(config: com.typesafe.config.Config): Config = {
        val additionalConfigFile = config.getString("devices.parameters.additional-config")
        val additionalConfig = if (additionalConfigFile.nonEmpty) {
          com.typesafe.config.ConfigFactory.parseResourcesAnySyntax(additionalConfigFile)
        } else {
          com.typesafe.config.ConfigFactory.empty()
        }

        Config(
          api = Api(
            interface = config.getString("api.interface"),
            port = config.getInt("api.port"),
            context = EndpointContext(config.getConfig("api.context"))
          ),
          devices = Devices(
            codeSize = config.getInt("devices.code-size"),
            codeExpiration = config.getDuration("devices.code-expiration").toMillis.millis,
            secretSize = config.getInt("devices.secret-size"),
            parameters = DeviceBootstrapParameters(
              authentication = DeviceBootstrapParameters.Authentication(
                tokenEndpoint = config.getString("devices.parameters.authentication.token-endpoint"),
                clientId = "", // provided during bootstrap execution
                clientSecret = "", // provided during bootstrap execution
                useQueryString = config.getBoolean("devices.parameters.authentication.use-query-string"),
                scopes = DeviceBootstrapParameters.Scopes(
                  api = config.getString("devices.parameters.authentication.scopes.api"),
                  core = config.getString("devices.parameters.authentication.scopes.core")
                ),
                context = EndpointContext.Encoded(
                  config = config.getConfig("devices.parameters.authentication.context")
                )
              ),
              serverApi = DeviceBootstrapParameters.ServerApi(
                url = config.getString("devices.parameters.server-api.url"),
                user = "", // provided during bootstrap execution
                userSalt = "", // provided during bootstrap execution
                device = "", // provided during bootstrap execution
                context = EndpointContext.Encoded(
                  config = config.getConfig("devices.parameters.server-api.context")
                )
              ),
              serverCore = DeviceBootstrapParameters.ServerCore(
                address = config.getString("devices.parameters.server-core.address"),
                nodeId = "", // provided during bootstrap execution
                context = EndpointContext.Encoded(
                  config = config.getConfig("devices.parameters.server-core.context")
                )
              ),
              secrets = SecretsConfig(
                config = config.getConfig("devices.parameters.secrets"),
                ivSize = SecretsConfig.Encryption.MinIvSize // value always overridden by client
              ),
              additionalConfig = Json
                .parse(additionalConfig.root().render(typesafe.ConfigRenderOptions.concise()))
                .as[JsObject]
            )
          )
        )
      }
    }
  }
}
