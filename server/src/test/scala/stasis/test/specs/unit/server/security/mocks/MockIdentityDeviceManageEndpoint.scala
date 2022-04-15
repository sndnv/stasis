package stasis.test.specs.unit.server.security.mocks

import java.util.UUID

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import stasis.core.security.tls.EndpointContext
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.server.security.mocks.MockIdentityDeviceManageEndpoint._

class MockIdentityDeviceManageEndpoint(
  port: Int,
  credentials: OAuth2BearerToken,
  existingDevice: Option[Device],
  searchResult: SearchResult,
  creationResult: CreationResult,
  updateResult: UpdateResult,
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

  existingDevice match {
    case Some(device) =>
      searchResult match {
        case SearchResult.SingleSubject =>
          wireMockServer.stubFor(
            get(s"/manage/clients/search/by-subject/${device.node}")
              .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
              .willReturn(okJson(s"""[{"id":"${UUID.randomUUID()}"}]"""))
          )

        case SearchResult.MultiSubject =>
          wireMockServer.stubFor(
            get(s"/manage/clients/search/by-subject/${device.node}")
              .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
              .willReturn(okJson(s"""[{"id":"${UUID.randomUUID()}"},{"id":"${UUID.randomUUID()}"}]"""))
          )

        case SearchResult.Invalid =>
          wireMockServer.stubFor(
            get(s"/manage/clients/search/by-subject/${device.node}")
              .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
              .willReturn(okJson(s"""{"a":"b"}"""))
          )

        case SearchResult.Failure =>
          wireMockServer.stubFor(
            get(s"/manage/clients/search/by-subject/${device.node}")
              .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
              .willReturn(serverError())
          )
      }

    case None =>
      wireMockServer.stubFor(
        get(urlMatching(s"/manage/clients/search/by-subject/.*"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(okJson(s"""[]"""))
      )
  }

  creationResult match {
    case CreationResult.Success =>
      wireMockServer.stubFor(
        post("/manage/clients")
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .withRequestBody(matchingJsonPath("$.redirect_uri"))
          .withRequestBody(matchingJsonPath("$.token_expiration"))
          .withRequestBody(matchingJsonPath("$.raw_secret"))
          .withRequestBody(matchingJsonPath("$.subject"))
          .willReturn(okJson(s"""{"client":"${UUID.randomUUID()}"}"""))
      )

    case CreationResult.Failure =>
      wireMockServer.stubFor(
        post("/manage/clients")
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(serverError())
      )
  }

  updateResult match {
    case UpdateResult.Success =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/clients/.*/credentials"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .withRequestBody(matchingJsonPath("$.raw_secret"))
          .willReturn(ok())
      )

    case UpdateResult.Failure =>
      wireMockServer.stubFor(
        put(urlMatching(s"/manage/clients/.*/credentials"))
          .withHeader("Authorization", equalTo(s"Bearer ${credentials.token}"))
          .willReturn(serverError())
      )
  }

  def created: Int =
    wireMockServer
      .findAll(postRequestedFor(urlEqualTo("/manage/clients")))
      .size

  def updated: Int =
    wireMockServer
      .findAll(putRequestedFor(urlMatching(s"/manage/clients/.*/credentials")))
      .size

  def searched: Int =
    wireMockServer
      .findAll(getRequestedFor(urlMatching(s"/manage/clients/search/by-subject/.*")))
      .size
}

object MockIdentityDeviceManageEndpoint {
  sealed trait SearchResult
  object SearchResult {
    case object SingleSubject extends SearchResult
    case object MultiSubject extends SearchResult
    case object Invalid extends SearchResult
    case object Failure extends SearchResult
  }

  sealed trait CreationResult
  object CreationResult {
    case object Success extends CreationResult
    case object Failure extends CreationResult
  }

  sealed trait UpdateResult
  object UpdateResult {
    case object Success extends UpdateResult
    case object Failure extends UpdateResult
  }

  def apply(
    port: Int,
    credentials: OAuth2BearerToken,
    existingDevice: Option[Device],
    searchResult: SearchResult = SearchResult.SingleSubject,
    creationResult: CreationResult = CreationResult.Success,
    updateResult: UpdateResult = UpdateResult.Success,
    withKeystoreConfig: Option[EndpointContext.StoreConfig] = None
  ): MockIdentityDeviceManageEndpoint =
    new MockIdentityDeviceManageEndpoint(
      port = port,
      credentials = credentials,
      existingDevice = existingDevice,
      searchResult = searchResult,
      creationResult = creationResult,
      updateResult = updateResult,
      withKeystoreConfig = withKeystoreConfig
    )
}
