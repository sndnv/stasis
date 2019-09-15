package stasis.core.security.jwt

import java.security.Key

import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.{JwtConsumer, JwtConsumerBuilder, JwtContext}
import org.jose4j.jwx.JsonWebStructure
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.keys.KeyProvider

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class JwtAuthenticator(
  provider: KeyProvider,
  audience: String,
  expirationTolerance: FiniteDuration
)(implicit ec: ExecutionContext) {

  private val extractor: JwtConsumer = new JwtConsumerBuilder()
    .setDisableRequireSignature()
    .setSkipAllValidators()
    .setSkipSignatureVerification()
    .build()

  def authenticate(credentials: String): Future[JwtClaims] = {
    val result = for {
      context <- Future { extractor.process(credentials) }
      keyId <- extractKeyId(context)
      key <- provider.key(id = keyId)
      claims <- process(context, key)
    } yield {
      claims
    }

    result
      .recoverWith {
        case NonFatal(e) =>
          Future.failed(AuthenticationFailure(s"Failed to authenticate token: [$e]"))
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
            ConstraintType.WHITELIST,
            provider.allowedAlgorithms: _*
          )
        )
        .build()

      val _ = consumer.processContext(context)
      context.getJwtClaims
    }
}
