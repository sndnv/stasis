package stasis.test.specs.unit.core.security.jwt.mocks

import java.security.PublicKey

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.jose4j.jwk.JsonWebKeySet

import scala.collection.JavaConverters._

class MockJwksEndpoint(port: Int, rsaKeysCount: Int = 3, ecKeysCount: Int = 3, secretKeysCount: Int = 3) {
  private val wireMockServer = new WireMockServer(new WireMockConfiguration().port(port))

  def start(): Unit = wireMockServer.start()

  def stop(): Unit = wireMockServer.stop()

  def url: String = s"http://localhost:$port"

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
          .withStatus(200)
          .withHeader("Content-Type", "application/json"))
  )

  wireMockServer.stubFor(
    get(urlEqualTo("/invalid/jwks.json"))
      .willReturn(serverError())
  )
}
