package stasis.server.security

import scala.concurrent.Future

import io.github.sndnv.layers.security.jwt.JwtProvider

import stasis.test.specs.unit.AsyncUnitSpec

class HttpCredentialsProviderSpec extends AsyncUnitSpec {
  "A Default HttpCredentialsProvider" should "provide http credentials" in {
    val expectedToken = "test-token"
    val expectedScope = "test-scope"

    val underlying = new JwtProvider {
      override def provide(scope: String): Future[String] = Future.successful(s"$expectedToken;$scope")
    }

    val provider = HttpCredentialsProvider.Default(
      scope = expectedScope,
      underlying = underlying
    )

    provider.provide().map { credentials =>
      credentials.token() should be(
        s"$expectedToken;$expectedScope"
      )
    }
  }
}
