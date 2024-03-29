package stasis.core.security.jwt

import java.security.Key
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.{JwtConsumer, JwtConsumerBuilder, JwtContext}
import org.jose4j.jwx.JsonWebStructure
import stasis.core.security.Metrics
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.keys.KeyProvider
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

class DefaultJwtAuthenticator(
  provider: KeyProvider,
  audience: String,
  override val identityClaim: String,
  expirationTolerance: FiniteDuration
)(implicit ec: ExecutionContext, telemetry: TelemetryContext)
    extends JwtAuthenticator {

  private val name = s"${this.getClass.getSimpleName} - $audience"

  private val extractor: JwtConsumer = new JwtConsumerBuilder()
    .setDisableRequireSignature()
    .setSkipAllValidators()
    .setSkipSignatureVerification()
    .build()

  private val metrics = telemetry.metrics[Metrics.Authenticator]

  override def authenticate(credentials: String): Future[JwtClaims] = {
    val result = for {
      context <- Future { extractor.process(credentials) }
      keyId <- extractKeyId(context)
      key <- provider.key(id = keyId)
      claims <- process(context, key)
    } yield {
      metrics.recordAuthentication(authenticator = name, successful = true)
      claims
    }

    result
      .recoverWith { case NonFatal(e) =>
        metrics.recordAuthentication(authenticator = name, successful = false)
        Future.failed(
          AuthenticationFailure(
            s"Failed to authenticate token: [${e.getClass.getSimpleName}: ${e.getMessage}]"
          )
        )
      }
  }

  private def extractKeyId(context: JwtContext): Future[Option[String]] =
    Future.fromTry(
      Try {
        context.getJoseObjects.asScala
          .collectFirst {
            case struct: JsonWebStructure if struct.getKeyIdHeaderValue != null =>
              struct.getKeyIdHeaderValue
          }
      }
    )

  private def process(context: JwtContext, key: Key): Future[JwtClaims] =
    Future {
      val consumer = new JwtConsumerBuilder()
        .setExpectedIssuer(provider.issuer)
        .setExpectedAudience(audience)
        .setRequireSubject()
        .setRequireExpirationTime()
        .setAllowedClockSkewInSeconds(expirationTolerance.toSeconds.toInt)
        .setVerificationKey(key)
        .setJwsAlgorithmConstraints(
          new AlgorithmConstraints(
            ConstraintType.PERMIT,
            provider.allowedAlgorithms: _*
          )
        )
        .build()

      val _ = consumer.processContext(context)
      val claims = context.getJwtClaims

      require(
        claims.hasClaim(identityClaim),
        s"Required identity claim [$identityClaim] was not found in [${claims.toString}]"
      )

      claims
    }
}

object DefaultJwtAuthenticator {
  def apply(
    provider: KeyProvider,
    audience: String,
    identityClaim: String,
    expirationTolerance: FiniteDuration
  )(implicit ec: ExecutionContext, telemetry: TelemetryContext): DefaultJwtAuthenticator =
    new DefaultJwtAuthenticator(
      provider = provider,
      audience = audience,
      identityClaim = identityClaim,
      expirationTolerance = expirationTolerance
    )
}
