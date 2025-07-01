package stasis.shared.model.devices

import play.api.libs.json.JsObject

import io.github.sndnv.layers.security.tls.EndpointContext
import stasis.shared.secrets.SecretsConfig

final case class DeviceBootstrapParameters(
  authentication: DeviceBootstrapParameters.Authentication,
  serverApi: DeviceBootstrapParameters.ServerApi,
  serverCore: DeviceBootstrapParameters.ServerCore,
  secrets: SecretsConfig,
  additionalConfig: JsObject
) {
  def withDeviceInfo(
    device: String,
    nodeId: String,
    clientId: String,
    clientSecret: String
  ): DeviceBootstrapParameters =
    copy(
      authentication = authentication.copy(
        clientId = clientId,
        clientSecret = clientSecret
      ),
      serverApi = serverApi.copy(
        device = device
      ),
      serverCore = serverCore.copy(
        nodeId = nodeId
      )
    )

  def withUserInfo(
    user: String,
    userSalt: String
  ): DeviceBootstrapParameters =
    copy(
      serverApi = serverApi.copy(
        user = user,
        userSalt = userSalt
      )
    )
}

object DeviceBootstrapParameters {
  final case class Authentication(
    tokenEndpoint: String,
    clientId: String,
    clientSecret: String,
    useQueryString: Boolean,
    scopes: Scopes,
    context: EndpointContext.Encoded
  )

  final case class ServerApi(
    url: String,
    user: String,
    userSalt: String,
    device: String,
    context: EndpointContext.Encoded
  )

  final case class ServerCore(
    address: String,
    nodeId: String,
    context: EndpointContext.Encoded
  )

  final case class Scopes(
    api: String,
    core: String
  )
}
