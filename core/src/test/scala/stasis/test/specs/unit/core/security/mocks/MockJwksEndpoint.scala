package stasis.test.specs.unit.core.security.mocks

import java.security.PublicKey

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.jose4j.jwk.JsonWebKeySet
import stasis.core.security.tls.EndpointContext

import scala.collection.JavaConverters._

class MockJwksEndpoint(
  port: Int,
  rsaKeysCount: Int = 3,
  ecKeysCount: Int = 3,
  secretKeysCount: Int = 3,
  withKeystoreConfig: Option[EndpointContext.StoreConfig] = None
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

  private val scheme = withKeystoreConfig match {
    case Some(_) => "https"
    case None    => "http"
  }

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  def url: String = s"$scheme://localhost:$port"

  val jwks: JsonWebKeySet = MockJwksGenerators.generateKeySet(rsaKeysCount, ecKeysCount, secretKeysCount)

  val keys: Map[String, PublicKey] = {
    jwks.getJsonWebKeys.asScala
      .map(c => (c.getKeyId, c.getKey))
      .collect {
        case (keyId: String, key: PublicKey) =>
          (keyId, key)
      }
      .toMap
  }

  wireMockServer.stubFor(
    get(urlEqualTo("/valid/jwks.json"))
      .willReturn(
        okJson(jwks.toJson)
      )
  )

  wireMockServer.stubFor(
    get(urlEqualTo("/invalid/jwks.json"))
      .willReturn(serverError())
  )
}
