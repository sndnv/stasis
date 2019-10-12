package stasis.test.specs.unit.core.security.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.jose4j.jwk.JsonWebKey
import stasis.core.security.tls.EndpointContext

class MockJwtEndpoint(
  port: Int,
  subject: String,
  secret: String,
  expirationSeconds: Long,
  signatureKey: JsonWebKey,
  withKeystoreConfig: Option[EndpointContext.StoreConfig]
) {
  private val config = withKeystoreConfig match {
    case Some(keystoreConfig) =>
      new WireMockConfiguration()
        .httpsPort(port)
        .keystorePath(keystoreConfig.storePath)
        .keystoreType(keystoreConfig.storeType)
        .keystorePassword(keystoreConfig.storePassword)

    case None =>
      new WireMockConfiguration()
        .port(port)
  }

  private val wireMockServer = new WireMockServer(config)

  private val urlQuery = s"grant_type=client_credentials&scope=$subject"

  private val scheme = withKeystoreConfig match {
    case Some(_) => "https"
    case None    => "http"
  }

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  def url: String = s"$scheme://localhost:$port"

  def count(path: String): Int =
    wireMockServer
      .findAll(postRequestedFor(urlEqualTo(s"$path?$urlQuery")))
      .size

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token?$urlQuery"))
      .withBasicAuth(subject, secret)
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "${generateJwt()}",
             |  "expires_in": $expirationSeconds,
             |  "scope": "$subject"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token/invalid?$urlQuery"))
      .willReturn(okXml("<test>data</test>"))
      .withBasicAuth(subject, secret)
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token/error?$urlQuery"))
      .willReturn(serverError())
      .withBasicAuth(subject, secret)
  )

  private def generateJwt(): String =
    MockJwtGenerators.generateJwt(
      issuer = "some-issuer",
      audience = "some-audience",
      subject = subject,
      signatureKey = signatureKey
    )
}
