package stasis.core.security.keys

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.http.scaladsl.{Http, HttpsConnectionContext}
import org.apache.pekko.util.Timeout
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jws.AlgorithmIdentifiers
import org.slf4j.LoggerFactory
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.security.Metrics
import stasis.core.security.exceptions.ProviderFailure
import stasis.core.security.tls.EndpointContext
import stasis.core.streaming.Operators.ExtendedSource
import stasis.core.telemetry.TelemetryContext

import java.security.Key
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

final class RemoteKeyProvider(
  jwksEndpoint: String,
  context: Option[EndpointContext],
  refreshInterval: FiniteDuration,
  refreshRetryInterval: FiniteDuration,
  override val issuer: String,
  override val allowedAlgorithms: Seq[String]
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, timeout: Timeout)
    extends KeyProvider {

  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private val metrics = telemetry.metrics[Metrics.KeyProvider]

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
        metrics.recordKeyRefresh(provider = jwksEndpoint, successful = true)

        scheduleKeysRefresh(delay = refreshInterval)
        Done
      }
      .recover { case NonFatal(e) =>
        metrics.recordKeyRefresh(provider = jwksEndpoint, successful = false)

        log.error(
          "Failed to load keys from JWKs endpoint [{}]: [{} - {}]",
          jwksEndpoint,
          e.getClass.getSimpleName,
          e.getMessage
        )
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
                log.debugN(
                  "JWKs endpoint [{}] provided key [{}] with algorithm [{}]",
                  jwksEndpoint,
                  jwk.id,
                  jwk.key.getAlgorithm
                )
                cache.put(jwk.id, jwk.key)

              case Left(failure) =>
                log.warnN("JWKs endpoint [{}] provided key that could not be handled: [{}]", jwksEndpoint, failure)
                Future.successful(Done)
            }
          )
          .map(_ => Done)
      }
}

object RemoteKeyProvider {
  def apply(
    jwksEndpoint: String,
    context: Option[EndpointContext],
    refreshInterval: FiniteDuration,
    refreshRetryInterval: FiniteDuration,
    issuer: String
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, timeout: Timeout): RemoteKeyProvider =
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
      }.toSeq
    }

  def getRawJwks(
    jwksEndpoint: String,
    context: Option[EndpointContext]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): Future[String] = {
    import system.executionContext

    val http = Http()

    val clientContext: HttpsConnectionContext = context match {
      case Some(context) => context.connection
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
      .flatMap { case HttpResponse(status, _, entity, _) =>
        status match {
          case StatusCodes.OK =>
            Unmarshal(entity).to[String]

          case _ =>
            entity.dataBytes.cancelled().flatMap { _ =>
              Future.failed(ProviderFailure(s"Endpoint responded with unexpected status: [${status.value}]"))
            }
        }
      }
  }
}
