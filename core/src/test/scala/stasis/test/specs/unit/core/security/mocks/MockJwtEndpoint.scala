package stasis.test.specs.unit.core.security.mocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.jose4j.jwk.JsonWebKey
import stasis.core.security.tls.EndpointContext

class MockJwtEndpoint(
  port: Int,
  credentials: MockJwtEndpoint.ExpectedCredentials,
  expirationSeconds: Long,
  signatureKey: JsonWebKey,
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

  private val clientCredentialsQuery =
    s"scope=${credentials.subject}&grant_type=client_credentials"

  private val clientCredentialsQueryWithoutScope =
    s"grant_type=client_credentials"

  private val resourceOwnerPasswordCredentialsQuery =
    s"scope=${credentials.subject}&grant_type=password&username=${credentials.user}&password=${credentials.userPassword}"

  private val refreshQuery =
    s"scope=${credentials.subject}&grant_type=refresh_token&refresh_token=${credentials.refreshToken}"

  private val scheme = withKeystoreConfig match {
    case Some(_) => "https"
    case None    => "http"
  }

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  def url: String = s"$scheme://localhost:$port"

  def count(path: String): Int =
    wireMockServer
      .findAll(postRequestedFor(urlPathMatching(path)))
      .size

  wireMockServer.stubFor(
    post(urlEqualTo("/token"))
      .withBasicAuth(credentials.subject, credentials.secret)
      .withRequestBody(containing("grant_type=client_credentials"))
      .withRequestBody(containing(s"scope=${credentials.subject}"))
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "${generateJwt()}",
             |  "expires_in": $expirationSeconds,
             |  "scope": "${credentials.subject}"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token?$clientCredentialsQuery"))
      .withBasicAuth(credentials.subject, credentials.secret)
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "${generateJwt()}",
             |  "expires_in": $expirationSeconds,
             |  "scope": "${credentials.subject}"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token?$clientCredentialsQueryWithoutScope"))
      .withBasicAuth(credentials.subject, credentials.secret)
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "${generateJwt()}",
             |  "expires_in": $expirationSeconds
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token?$resourceOwnerPasswordCredentialsQuery"))
      .withBasicAuth(credentials.subject, credentials.secret)
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "${generateJwt()}",
             |  "expires_in": $expirationSeconds,
             |  "scope": "${credentials.subject}"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token?$refreshQuery"))
      .withBasicAuth(credentials.subject, credentials.secret)
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "${generateJwt()}",
             |  "expires_in": $expirationSeconds,
             |  "scope": "${credentials.subject}"
             |}
           """.stripMargin
        )
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token/invalid?$clientCredentialsQuery"))
      .willReturn(okXml("<test>data</test>"))
      .withBasicAuth(credentials.subject, credentials.secret)
  )

  wireMockServer.stubFor(
    post(urlEqualTo(s"/token/error?$clientCredentialsQuery"))
      .willReturn(serverError())
      .withBasicAuth(credentials.subject, credentials.secret)
  )

  private def generateJwt(): String =
    MockJwtGenerators.generateJwt(
      issuer = "some-issuer",
      audience = "some-audience",
      subject = credentials.subject,
      signatureKey = signatureKey
    )
}

object MockJwtEndpoint {
  final case class ExpectedCredentials(
    subject: String,
    secret: String,
    refreshToken: String,
    user: String,
    userPassword: String
  )
}
