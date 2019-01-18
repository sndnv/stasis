package stasis.security.jwt

import java.security.Key
import java.util.UUID

import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.{JwtConsumer, JwtConsumerBuilder, JwtContext}
import stasis.routing.Node
import stasis.security.NodeAuthenticator
import stasis.security.exceptions.AuthenticationFailure

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

class JwtAuthenticator(
  provider: JwtKeyProvider,
  audience: String,
  expirationTolerance: FiniteDuration
)(implicit ec: ExecutionContext)
    extends NodeAuthenticator[String] {

  private val extractor: JwtConsumer = new JwtConsumerBuilder()
    .setDisableRequireSignature()
    .setSkipAllValidators()
    .setSkipSignatureVerification()
    .build()

  override def authenticate(credentials: String): Future[Node.Id] = {
    val result = for {
      context <- Future {
        extractor.process(credentials)
      }
      keyId <- Future.fromTry(extractKeyId(context))
      key <- provider.key(id = keyId)
      claims <- process(context, key)
      node <- Future.fromTry(getNodeFromClaims(claims))
    } yield {
      node
    }

    result
      .recoverWith {
        case e: AuthenticationFailure => Future.failed(e)
        case NonFatal(e)              => Future.failed(AuthenticationFailure(s"Failed to authenticate token: [$e]"))
      }
  }

  private def extractKeyId(context: JwtContext): Try[Option[String]] =
    Try {
      context.getJoseObjects.asScala
        .collectFirst {
          case struct if struct.getKeyIdHeaderValue != null =>
            struct.getKeyIdHeaderValue
        }
    }

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

  private def getNodeFromClaims(claims: JwtClaims): Try[Node.Id] =
    for {
      subject <- Try(claims.getSubject)
      node <- Try(UUID.fromString(subject)).recoverWith {
        case _: IllegalArgumentException =>
          Failure(AuthenticationFailure(s"Invalid node ID encountered: [$subject]"))
      }
    } yield {
      node
    }
}
