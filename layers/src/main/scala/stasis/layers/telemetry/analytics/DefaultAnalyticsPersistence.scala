package stasis.layers.telemetry.analytics

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.DispatcherSelector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import stasis.layers.api.Formats.analyticsEntryFormat

class DefaultAnalyticsPersistence(
  config: DefaultAnalyticsPersistence.Config
)(implicit system: ActorSystem[Nothing])
    extends AnalyticsPersistence {
  override type Self = this.type

  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.blocking())

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val lastCachedRef: AtomicReference[Instant] = new AtomicReference(Instant.EPOCH)
  private val lastTransmittedRef: AtomicReference[Instant] = new AtomicReference(Instant.EPOCH)
  private val clientProviderRef: AtomicReference[Option[AnalyticsClient.Provider]] = new AtomicReference(None)

  override def cache(entry: AnalyticsEntry): Unit = {
    val cachedAt = Instant.now()

    val result = Try {
      val permissions = PosixFilePermissions.fromString(DefaultAnalyticsPersistence.Defaults.CacheFilePermissions)
      val _ = Files.createFile(config.localCache, PosixFilePermissions.asFileAttribute(permissions))

      val content = Json.toBytes(
        Json.toJson(entry).as[JsObject] ++ Json.obj(
          "last_cached" -> Json.toJson(cachedAt),
          "last_transmitted" -> Json.toJson(lastTransmittedRef.get())
        )
      )

      Files.write(
        config.localCache,
        content,
        DefaultAnalyticsPersistence.Defaults.CacheFileWriteOptions: _*
      )
    }

    result match {
      case Success(_) =>
        lastCachedRef.set(cachedAt)

        log.debug(
          "Analytics entry [{}] successfully cached to [{}]",
          entry.runtime.id,
          config.localCache
        )

      case Failure(e) =>
        log.warn(
          "Failed to cache analytics entry [{}] to [{}]: [{} - {}]",
          entry.runtime.id,
          config.localCache,
          e.getClass.getSimpleName,
          e.getMessage
        )
    }
  }

  override def transmit(entry: AnalyticsEntry): Future[Done] = {
    val updated = if (config.keepEvents) entry else entry.asCollected().discardEvents()
    val outgoing = if (config.keepFailures) updated else updated.asCollected().discardFailures()

    def send(retries: Int): Future[Done] =
      clientProviderRef.get() match {
        case Some(provider) =>
          provider.client.sendAnalyticsEntry(entry = outgoing).map { result =>
            lastTransmittedRef.set(Instant.now())
            result
          }

        case None if retries > 0 =>
          log.debug("Analytics entry could not be transmitted; client provider not available")
          org.apache.pekko.pattern.after(duration = config.clientProviderRetryInterval)(
            send(retries = retries - 1)
          )

        case None =>
          Future.failed(
            new IllegalStateException(
              s"Expected a client provider but none was found after [${config.clientProviderMaxRetries.toString}] retries"
            )
          )
      }

    send(retries = config.clientProviderMaxRetries)
  }

  override def restore(): Future[Option[AnalyticsEntry]] = Future {
    import DefaultAnalyticsPersistence.ExtendedInstant

    if (Files.exists(config.localCache)) {
      val content = Files.readAllBytes(config.localCache)
      val entry = Json.parse(content).as[JsObject]

      {
        val stored = (entry \ "last_cached").asOpt[Instant].getOrElse(Instant.EPOCH)
        val _ = lastCachedRef.updateAndGet(existing => stored.max(existing))
      }

      {
        val stored = (entry \ "last_transmitted").asOpt[Instant].getOrElse(Instant.EPOCH)
        val _ = lastTransmittedRef.updateAndGet(existing => stored.max(existing))
      }

      Some(entry.as[AnalyticsEntry])
    } else {
      None
    }
  }

  override def lastCached: Instant =
    lastCachedRef.get()

  override def lastTransmitted: Instant =
    lastTransmittedRef.get()

  override def withClientProvider(provider: AnalyticsClient.Provider): Self = {
    clientProviderRef.set(Some(provider))
    this
  }
}

object DefaultAnalyticsPersistence {
  def apply(
    config: DefaultAnalyticsPersistence.Config
  )(implicit system: ActorSystem[Nothing]): DefaultAnalyticsPersistence =
    new DefaultAnalyticsPersistence(config = config)

  final case class Config(
    localCache: Path,
    keepEvents: Boolean,
    keepFailures: Boolean,
    clientProviderRetryInterval: FiniteDuration,
    clientProviderMaxRetries: Int
  )

  object Config {
    def apply(
      localCache: Path,
      keepEvents: Boolean,
      keepFailures: Boolean
    ): Config =
      Config(
        localCache = localCache,
        keepEvents = keepEvents,
        keepFailures = keepFailures,
        clientProviderRetryInterval = Defaults.ClientProviderRetryInterval,
        clientProviderMaxRetries = Defaults.ClientProviderMaxRetries
      )
  }

  private object Defaults {
    val CacheFilePermissions: String = "rw-------"

    val CacheFileWriteOptions: Seq[StandardOpenOption] = Seq(
      StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING
    )

    val ClientProviderRetryInterval: FiniteDuration = 200.millis
    val ClientProviderMaxRetries: Int = 10
  }

  implicit class ExtendedInstant(instant: Instant) {
    def min(other: Instant): Instant =
      if (instant.isBefore(other)) instant
      else other

    def max(other: Instant): Instant =
      if (instant.isAfter(other)) instant
      else other
  }
}
