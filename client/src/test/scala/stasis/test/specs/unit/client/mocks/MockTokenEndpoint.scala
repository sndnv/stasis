package stasis.test.specs.unit.client.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

class MockTokenEndpoint(port: Int, token: String) {

  private val wireMockServer = new WireMockServer(new WireMockConfiguration().port(port))

  def url: String = s"http://localhost:$port"

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  wireMockServer.stubFor(
    post(anyUrl())
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
}
