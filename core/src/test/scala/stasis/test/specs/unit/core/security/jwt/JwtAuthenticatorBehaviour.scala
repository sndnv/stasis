package stasis.test.specs.unit.core.security.jwt

import org.jose4j.jwk.JsonWebKey
import stasis.core.security.jwt.DefaultJwtAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwkProvider, MockJwtGenerators}
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait JwtAuthenticatorBehaviour {
  _: AsyncUnitSpec =>
  def authenticator(withKeyType: String, withJwk: JsonWebKey): Unit = {
    it should s"successfully authenticate valid tokens ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val authenticator = new DefaultJwtAuthenticator(
        provider = MockJwkProvider(withJwk),
        audience = "self",
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )

      val expectedSubject = "some-subject"
      val token: String = MockJwtGenerators.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedSubject,
        signatureKey = withJwk
      )

      for {
        claims <- authenticator.authenticate(credentials = token)
      } yield {
        claims.getSubject should be(expectedSubject)
        telemetry.security.authenticator.authentication should be(1)
      }
    }

    it should s"refuse authentication attempts with invalid tokens ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val authenticator = new DefaultJwtAuthenticator(
        provider = MockJwkProvider(withJwk),
        audience = "self",
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )

      val actualAudience = "some-audience"
      val token: String = MockJwtGenerators.generateJwt(
        issuer = "self",
        audience = actualAudience,
        subject = "some-subject",
        signatureKey = withJwk
      )

      authenticator
        .authenticate(credentials = token)
        .map { response =>
          fail(s"Received unexpected response from authenticator: [$response]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should startWith("Failed to authenticate token")

          e.getMessage should include(
            s"Audience (aud) claim [$actualAudience] doesn't contain an acceptable identifier"
          )

          telemetry.security.authenticator.authentication should be(1)
        }
    }

    it should s"successfully authenticate valid tokens with custom identity claims ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val customIdentityClaim = "identity"

      val authenticator = new DefaultJwtAuthenticator(
        provider = MockJwkProvider(withJwk),
        audience = "self",
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )

      val expectedSubject = "some-subject"
      val expectedIdentity = "some-identity"
      val token: String = MockJwtGenerators.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedSubject,
        signatureKey = withJwk,
        customClaims = Map(customIdentityClaim -> expectedIdentity)
      )

      for {
        claims <- authenticator.authenticate(credentials = token)
      } yield {
        claims.getSubject should be(expectedSubject)
        claims.getClaimValue(customIdentityClaim, classOf[String]) should be(expectedIdentity)
        telemetry.security.authenticator.authentication should be(1)
      }
    }

    it should s"refuse authentication attempts when tokens have missing custom identity claims ($withKeyType)" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val customIdentityClaim = "identity"

      val authenticator = new DefaultJwtAuthenticator(
        provider = MockJwkProvider(withJwk),
        audience = "self",
        identityClaim = customIdentityClaim,
        expirationTolerance = 10.seconds
      )

      val token: String = MockJwtGenerators.generateJwt(
        issuer = "self",
        audience = "self",
        subject = "some-subject",
        signatureKey = withJwk
      )

      authenticator
        .authenticate(credentials = token)
        .map { response =>
          fail(s"Received unexpected response from authenticator: [$response]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should startWith("Failed to authenticate token")

          e.getMessage should include(
            s"Required identity claim [$customIdentityClaim] was not found"
          )

          telemetry.security.authenticator.authentication should be(1)
        }
    }
  }
}
