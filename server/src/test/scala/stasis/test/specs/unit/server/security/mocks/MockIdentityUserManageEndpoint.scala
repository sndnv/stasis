package stasis.test.specs.unit.server.security.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.layers.security.tls.EndpointContext
import stasis.test.specs.unit.server.security.mocks.MockIdentityUserManageEndpoint._

class MockIdentityUserManageEndpoint(
  port: Int,
  credentials: OAuth2BearerToken,
  creationResult: CreationResult,
  activationResult: ActivationResult,
  deactivationResult: DeactivationResult,
  passwordUpdateResult: PasswordUpdateResult,
  withKeystoreConfig: Option[EndpointContext.StoreConfig]
) {
  private val config = withKeystoreConfig match {
    case Some(keystoreConfig) =>
      new WireMockConfiguration()
        .httpDisabled(true)
        .httpsPort(port)
        .keystorePath(keystoreConfig.storePath)
        .keystoreType(keystoreConfig.storeType)
        .keystorePassword(keystoreConfig.storePassword)
        .keyManagerPassword(keystoreConfig.storePassword)

    case None =>
      new WireMockConfiguration()
        .port(port)
  }

  private val wireMockServer = new WireMockServer(config)

  private val scheme = withKeystoreConfig match {
    case Some(_) => "https"
    case None    => "http"
  }

  def url: String = s"$scheme://localhost:$port"

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  creationResult match {
    case CreationResult.Success =>
      wireMockServer.stubFor(
        post("/manage/owners")
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .withRequestBody(matchingJsonPath("$.username"))
          .withRequestBody(matchingJsonPath("$.raw_password"))
          .withRequestBody(matchingJsonPath("$..allowed_scopes"))
          .withRequestBody(matchingJsonPath("$.subject"))
          .willReturn(ok())
      )

    case CreationResult.Failure =>
      wireMockServer.stubFor(
        post("/manage/owners")
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(serverError())
      )
  }

  activationResult match {
    case ActivationResult.Success =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/owners/by-subject/.*/activate"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(ok())
      )

    case ActivationResult.Failure =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/owners/by-subject/.*/activate"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(serverError())
      )
  }

  deactivationResult match {
    case DeactivationResult.Success =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/owners/by-subject/.*/deactivate"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(ok())
      )

    case DeactivationResult.Failure =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/owners/by-subject/.*/deactivate"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(serverError())
      )
  }

  passwordUpdateResult match {
    case PasswordUpdateResult.Success =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/owners/by-subject/.*/credentials"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .withRequestBody(matchingJsonPath("$.raw_password"))
          .willReturn(ok())
      )

    case PasswordUpdateResult.Failure =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/owners/by-subject/.*/credentials"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(serverError())
      )
  }

  def created: Int =
    wireMockServer
      .findAll(postRequestedFor(urlEqualTo("/manage/owners")))
      .size

  def activated: Int =
    wireMockServer
      .findAll(putRequestedFor(urlMatching(s"/manage/owners/by-subject/.*/activate")))
      .size

  def deactivated: Int =
    wireMockServer
      .findAll(putRequestedFor(urlMatching(s"/manage/owners/by-subject/.*/deactivate")))
      .size

  def passwordUpdated: Int =
    wireMockServer
      .findAll(putRequestedFor(urlMatching(s"/manage/owners/by-subject/.*/credentials")))
      .size
}

object MockIdentityUserManageEndpoint {
  sealed trait CreationResult
  object CreationResult {
    case object Success extends CreationResult
    case object Failure extends CreationResult
  }

  sealed trait ActivationResult
  object ActivationResult {
    case object Success extends ActivationResult
    case object Failure extends ActivationResult
  }

  sealed trait DeactivationResult
  object DeactivationResult {
    case object Success extends DeactivationResult
    case object Failure extends DeactivationResult
  }

  sealed trait PasswordUpdateResult
  object PasswordUpdateResult {
    case object Success extends PasswordUpdateResult
    case object Failure extends PasswordUpdateResult
  }

  def apply(
    port: Int,
    credentials: OAuth2BearerToken,
    creationResult: CreationResult = CreationResult.Success,
    activationResult: ActivationResult = ActivationResult.Success,
    deactivationResult: DeactivationResult = DeactivationResult.Success,
    passwordUpdateResult: PasswordUpdateResult = PasswordUpdateResult.Success,
    withKeystoreConfig: Option[EndpointContext.StoreConfig] = None
  ): MockIdentityUserManageEndpoint =
    new MockIdentityUserManageEndpoint(
      port = port,
      credentials = credentials,
      creationResult = creationResult,
      activationResult = activationResult,
      deactivationResult = deactivationResult,
      passwordUpdateResult = passwordUpdateResult,
      withKeystoreConfig = withKeystoreConfig
    )
}
