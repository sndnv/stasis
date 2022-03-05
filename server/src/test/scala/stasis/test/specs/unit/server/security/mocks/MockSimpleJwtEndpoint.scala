package stasis.test.specs.unit.server.security.mocks

import java.security.PublicKey

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.jose4j.jwk.JsonWebKeySet
import stasis.core.security.tls.EndpointContext
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators

import scala.jdk.CollectionConverters._

class MockSimpleJwtEndpoint(
  port: Int,
  token: String,
  withKeystoreConfig: Option[EndpointContext.StoreConfig] = None
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

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  def url: String = s"$scheme://localhost:$port"

  val jwks: JsonWebKeySet = MockJwksGenerators.generateKeySet(
    rsaKeysCount = 1,
    ecKeysCount = 0,
    secretKeysCount = 0
  )

  val keys: Map[String, PublicKey] =
    jwks.getJsonWebKeys.asScala
      .map(c => (c.getKeyId, c.getKey))
      .collect { case (keyId: String, key: PublicKey) =>
        (keyId, key)
      }
      .toMap

  wireMockServer.stubFor(
    get(urlEqualTo("/valid/jwks.json"))
      .willReturn(
        okJson(jwks.toJson)
      )
  )

  wireMockServer.stubFor(
    post(urlEqualTo("/oauth/token"))
      .willReturn(
        okJson(
          s"""
             |{
             |  "access_token": "$token",
             |  "expires_in": 90
             |}
           """.stripMargin
        )
      )
  )
}
