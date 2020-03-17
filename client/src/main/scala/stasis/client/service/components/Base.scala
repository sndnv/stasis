package stasis.client.service.components

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.scaladsl.adapter._
import akka.event.LoggingAdapter
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import stasis.client.analysis.Checksum
import stasis.client.compression.{Compression, Decoder => CompressionDecoder, Encoder => CompressionEncoder}
import stasis.client.encryption.{Aes, Decoder => EncryptionDecoder, Encoder => EncryptionEncoder}
import stasis.client.service.ApplicationDirectory
import stasis.client.staging.{DefaultFileStaging, FileStaging}
import stasis.client.tracking.trackers.DefaultTracker
import stasis.core.persistence.backends.memory.EventLogMemoryBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

trait Base {
  implicit def system: ActorSystem[SpawnProtocol]
  implicit def ec: ExecutionContext
  implicit def untyped: akka.actor.ActorSystem
  implicit def mat: Materializer
  implicit def log: LoggingAdapter

  def directory: ApplicationDirectory

  def configOverride: typesafe.Config
  def rawConfig: typesafe.Config

  implicit def timeout: Timeout

  def checksum: Checksum
  def compression: CompressionEncoder with CompressionDecoder
  def encryption: EncryptionEncoder with EncryptionDecoder
  def staging: FileStaging
  def tracker: DefaultTracker

  def terminateService: () => Unit

  implicit class OpToFuture[T](op: => T) {
    def future: Future[T] = Future.fromTry(Try(op))
  }

  implicit class TryOpToFuture[T](op: => Try[T]) {
    def future: Future[T] = Future.fromTry(op)
  }
}

object Base {
  def apply(
    applicationDirectory: ApplicationDirectory,
    terminate: () => Unit
  )(
    implicit typedSystem: ActorSystem[SpawnProtocol],
    loggingAdapter: LoggingAdapter
  ): Future[Base] =
    Future.fromTry(
      Try {
        new Base {
          override implicit val system: ActorSystem[SpawnProtocol] = typedSystem
          override implicit val ec: ExecutionContext = typedSystem.executionContext
          override implicit val untyped: akka.actor.ActorSystem = typedSystem.toUntyped
          override implicit val mat: Materializer = ActorMaterializer()
          override implicit val log: LoggingAdapter = loggingAdapter

          override val directory: ApplicationDirectory =
            applicationDirectory

          override val configOverride: typesafe.Config =
            loadConfigOverride(directory)

          override val rawConfig: typesafe.Config =
            configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve()

          override implicit val timeout: Timeout =
            rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

          override val checksum: Checksum =
            Checksum(rawConfig.getString("analysis.checksum"))

          override val compression: CompressionEncoder with CompressionDecoder =
            Compression(rawConfig.getString("compression.type"))

          override val encryption: EncryptionEncoder with EncryptionDecoder =
            Aes

          override val staging: FileStaging =
            new DefaultFileStaging(
              storeDirectory = Try(rawConfig.getString("staging.store-directory")).toOption.map(Paths.get(_)),
              prefix = rawConfig.getString("staging.files.prefix"),
              suffix = rawConfig.getString("staging.files.suffix")
            )

          override val tracker: DefaultTracker =
            DefaultTracker(
              createBackend = state =>
                EventLogMemoryBackend(
                  name = s"tracker-${java.util.UUID.randomUUID()}",
                  initialState = state
                )(typedSystem, timeout)
            )

          override val terminateService: () => Unit = terminate
        }
      }
    )

  def loadConfigOverride(directory: ApplicationDirectory): typesafe.Config =
    directory.findFile(file = Files.ConfigOverride) match {
      case Some(configFile) =>
        typesafe.ConfigFactory.parseString(
          java.nio.file.Files.readString(configFile, StandardCharsets.UTF_8)
        )

      case None =>
        typesafe.ConfigFactory.empty()
    }
}
