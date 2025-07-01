package stasis.test.specs.unit.client.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.github.sndnv.layers.security.oauth.OAuthClient.GrantType

class MockTokenEndpoint(port: Int, token: String, allowedGrants: Seq[String]) {
  private val wireMockServer = new WireMockServer(new WireMockConfiguration().port(port))

  def url: String = s"http://localhost:$port"

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  wireMockServer.stubFor(
    post(anyUrl())
      .withQueryParam("grant_type", matching(allowedGrants.mkString("|")))
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "$token",
             |  "expires_in": 42,
             |  "scope": "test-scope"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    get(anyUrl())
      .withQueryParam("grant_type", matching(allowedGrants.mkString("|")))
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "$token",
             |  "expires_in": 42,
             |  "scope": "test-scope"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(anyUrl())
      .withQueryParam("grant_type", notMatching(allowedGrants.mkString("|")))
      .willReturn(badRequest())
  )

  wireMockServer.stubFor(
    get(anyUrl())
      .withQueryParam("grant_type", notMatching(allowedGrants.mkString("|")))
      .willReturn(badRequest())
  )
}

object MockTokenEndpoint {
  def apply(port: Int, token: String): MockTokenEndpoint =
    MockTokenEndpoint(
      port = port,
      token = token,
      allowedGrants = Seq(
        GrantType.ClientCredentials,
        GrantType.RefreshToken,
        GrantType.ResourceOwnerPasswordCredentials
      )
    )

  def apply(port: Int, token: String, allowedGrants: Seq[String]): MockTokenEndpoint =
    new MockTokenEndpoint(port = port, token = token, allowedGrants = allowedGrants)
}
