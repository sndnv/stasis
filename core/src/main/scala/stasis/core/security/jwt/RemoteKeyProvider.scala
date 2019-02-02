package stasis.core.security.jwt

import java.security.Key

import akka.Done
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jws.AlgorithmIdentifiers
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.exceptions.ProviderFailure

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

final class RemoteKeyProvider(
  jwksEndpoint: String,
  refreshInterval: FiniteDuration,
  override val issuer: String,
  override val allowedAlgorithms: Seq[String]
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout)
    extends JwtKeyProvider {

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(untypedSystem, this.getClass.getName)

  private val backend = MemoryBackend[String, Key](name = "asymmetric-jwt-key-provider-cache")

  private val _ = refreshKeys()

  override def key(id: Option[String]): Future[Key] =
    id match {
      case Some(keyId) =>
        backend.get(keyId).flatMap {
          case Some(key) =>
            Future.successful(key)

          case None =>
            Future.failed(ProviderFailure(s"Key [$keyId] was not found"))
        }

      case None =>
        Future.failed(ProviderFailure("Key expected but none was provided"))
    }

  private def refreshKeys(): Future[Done] =
    RemoteKeyProvider
      .getRawJwks(jwksEndpoint)
      .flatMap(loadKeys)
      .recover {
        case NonFatal(e) =>
          log.error(s"Failed to load keys from JWKS endpoint [$jwksEndpoint]: [$e]")
          Done
      }
      .map { result =>
        scheduleKeysRefresh()
        result
      }

  private def scheduleKeysRefresh(): Unit = {
    val _ = system.scheduler.scheduleOnce(
      refreshInterval,
      () => {
        val _ = refreshKeys()
      }
    )
  }

  private def loadKeys(rawJwks: String): Future[Done] =
    Future
      .fromTry(RemoteKeyProvider.parseJwks(rawJwks))
      .flatMap { jwks =>
        Future
          .sequence(
            jwks.map {
              case Right(jwk) =>
                log.debug(
                  s"JWKS endpoint [$jwksEndpoint] provided key [${jwk.id}] with algorithm [${jwk.key.getAlgorithm}]")
                backend.put(jwk.id, jwk.key)

              case Left(failure) =>
                log.warning(s"JWKS endpoint provided key that could not be handled: [$failure]")
                Future.successful(Done)
            }
          )
          .map(_ => Done)
      }
}

object RemoteKeyProvider {
  def apply(
    jwksEndpoint: String,
    refreshInterval: FiniteDuration,
    issuer: String
  )(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout): RemoteKeyProvider =
    new RemoteKeyProvider(
      jwksEndpoint,
      refreshInterval,
      issuer,
      allowedAlgorithms = Seq(
        AlgorithmIdentifiers.RSA_USING_SHA256,
        AlgorithmIdentifiers.RSA_USING_SHA384,
        AlgorithmIdentifiers.RSA_USING_SHA512,
        AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256,
        AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384,
        AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512
      )
    )

  final case class Jwk(id: String, key: Key)
  final case class UnusableJwk(failure: String)

  def parseJwks(rawJwks: String): Try[Seq[Either[UnusableJwk, Jwk]]] =
    Try(new JsonWebKeySet(rawJwks)).map { jwks =>
      jwks.getJsonWebKeys.asScala.map { jwk =>
        Option(jwk.getKeyId) match {
          case Some(keyId) => Right(Jwk(keyId, jwk.getKey))
          case None        => Left(UnusableJwk(s"Found key of type [${jwk.getKeyType}] without an ID"))
        }
      }
    }

  def getRawJwks(
    jwksEndpoint: String
  )(implicit s: akka.actor.ActorSystem, m: ActorMaterializer): Future[String] = {
    import s.dispatcher

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = jwksEndpoint
        )
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              Unmarshal(entity).to[String]

            case _ =>
              val _ = entity.discardBytes()
              Future.failed(ProviderFailure(s"Endpoint responded with unexpected status: [${status.value}]"))
          }
      }
  }
}
