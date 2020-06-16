package stasis.test.specs.unit.core.networking.grpc.internal

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, CustomHeader, OAuth2BearerToken}
import akka.parboiled2.util.Base64
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.core.networking.grpc.internal.Credentials
import stasis.test.specs.unit.AsyncUnitSpec

import scala.util.control.NonFatal

class CredentialsSpec extends AsyncUnitSpec {
  it should "successfully marshal credentials" in {
    Credentials.marshal(BasicHttpCredentials(testNode, testSecret)) should be(basicCredentials)
    Credentials.marshal(OAuth2BearerToken(testToken)) should be(tokenCredentials)
  }

  it should "successfully unmarshal credentials" in {
    Credentials.unmarshal(basicCredentials) should be(Right(BasicHttpCredentials(testNode, testSecret)))
    Credentials.unmarshal(tokenCredentials) should be(Right(OAuth2BearerToken(testToken)))
  }

  it should "fail to unmarshal invalid basic credentials" in {
    val encoded = s"Basic ${encode(testNode)}"
    Credentials.unmarshal(encoded) should be(Left(CredentialsFailure("Failed to extract basic auth credentials")))
  }

  it should "fail to unmarshal credentials with an invalid format" in {
    val encoded = s"Test ${encode(testNode)}"
    Credentials.unmarshal(encoded) should be(Left(CredentialsFailure("Unexpected credentials format encountered")))
  }

  it should "extract credentials" in {
    val header = Authorization(BasicHttpCredentials(testNode, testSecret))
    val request = HttpRequest(headers = scala.collection.immutable.Seq(header))

    Credentials.extract(request).map { result =>
      result should be(BasicHttpCredentials(testNode, testSecret))
    }
  }

  it should "fail to extract invalid credentials" in {
    val header = new CustomHeader {
      override def name(): String = Credentials.HEADER

      override def value(): String = "invalid"

      override def renderInRequests(): Boolean = false

      override def renderInResponses(): Boolean = false
    }

    val request = HttpRequest(headers = scala.collection.immutable.Seq(header))

    Credentials
      .extract(request)
      .map { response =>
        fail(s"Unexpected response provided: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Unexpected credentials format encountered")
      }
  }

  it should "fail to extract missing credentials" in {
    val request = HttpRequest()
    Credentials
      .extract(request)
      .map { response =>
        fail(s"Unexpected response provided: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"No credentials provided")
      }
  }

  private val testNode = "test-node"
  private val testSecret = "test-secret"
  private val testToken = "test-token"

  private val basicCredentials = s"Basic ${encode(s"$testNode:$testSecret")}"
  private val tokenCredentials = s"Bearer $testToken"

  private def encode(raw: String): String =
    Base64.rfc2045().encodeToString(raw.getBytes(StandardCharsets.UTF_8), false)
}
