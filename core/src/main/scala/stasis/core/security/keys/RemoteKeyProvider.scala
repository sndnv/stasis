package stasis.core.security.keys

import java.security.Key

import akka.Done
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.Logging
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.{ByteString, Timeout}
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
  context: Option[HttpsConnectionContext],
  refreshInterval: FiniteDuration,
  refreshRetryInterval: FiniteDuration,
  override val issuer: String,
  override val allowedAlgorithms: Seq[String]
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout)
    extends KeyProvider {

  private implicit val untypedSystem: akka.actor.ActorSystem = system.toUntyped
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val log = Logging(untypedSystem, this.getClass.getName)

  private val cache = MemoryBackend[String, Key](name = "jwk-provider-cache")

  private val init = refreshKeys()

  override def key(id: Option[String]): Future[Key] =
    id match {
      case Some(keyId) if init.isCompleted => key(keyId)
      case Some(keyId)                     => init.flatMap(_ => key(keyId))
      case None                            => Future.failed(ProviderFailure("Key expected but none was provided"))
    }

  private def key(id: String): Future[Key] =
    cache.get(id).flatMap {
      case Some(key) => Future.successful(key)
      case None      => Future.failed(ProviderFailure(s"Key [$id] was not found"))
    }

  private def refreshKeys(): Future[Done] =
    RemoteKeyProvider
      .getRawJwks(jwksEndpoint, context)
      .flatMap(loadKeys)
      .map { _ =>
        scheduleKeysRefresh(delay = refreshInterval)
        Done
      }
      .recover {
        case NonFatal(e) =>
          log.error(e, "Failed to load keys from JWKs endpoint [{}]: [{}]", jwksEndpoint, e.getMessage)
          scheduleKeysRefresh(delay = refreshRetryInterval)
          Done
      }

  private def scheduleKeysRefresh(delay: FiniteDuration): Unit = {
    log.debug("Scheduling loading of keys from JWKs endpoint [{}] in [{}] second(s)", jwksEndpoint, delay.toSeconds)
    val _ = system.scheduler.scheduleOnce(
      delay = delay,
      runnable = () => {
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
                  "JWKs endpoint [{}] provided key [{}] with algorithm [{}}]",
                  jwksEndpoint,
                  jwk.id,
                  jwk.key.getAlgorithm
                )
                cache.put(jwk.id, jwk.key)

              case Left(failure) =>
                log.warning("JWKs endpoint [{}] provided key that could not be handled: [{}]", jwksEndpoint, failure)
                Future.successful(Done)
            }
          )
          .map(_ => Done)
      }
}

object RemoteKeyProvider {
  def apply(
    jwksEndpoint: String,
    context: Option[HttpsConnectionContext],
    refreshInterval: FiniteDuration,
    refreshRetryInterval: FiniteDuration,
    issuer: String
  )(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout): RemoteKeyProvider =
    new RemoteKeyProvider(
      jwksEndpoint = jwksEndpoint,
      context = context,
      refreshInterval = refreshInterval,
      refreshRetryInterval = refreshRetryInterval,
      issuer = issuer,
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
    jwksEndpoint: String,
    context: Option[HttpsConnectionContext]
  )(implicit s: akka.actor.ActorSystem, m: ActorMaterializer): Future[String] = {
    import s.dispatcher

    val http = Http()

    val clientContext: HttpsConnectionContext = context match {
      case Some(context) => context
      case None          => http.defaultClientHttpsContext
    }

    http
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = jwksEndpoint
        ),
        connectionContext = clientContext
      )
      .flatMap {
        case HttpResponse(status, _, entity, _) =>
          status match {
            case StatusCodes.OK =>
              Unmarshal(entity).to[String]

            case _ =>
              val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
              Future.failed(ProviderFailure(s"Endpoint responded with unexpected status: [${status.value}]"))
          }
      }
  }
}
