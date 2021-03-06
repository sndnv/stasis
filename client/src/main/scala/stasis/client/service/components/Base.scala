package stasis.client.service.components

import java.nio.file.Paths

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import org.slf4j.Logger
import stasis.client.analysis.Checksum
import stasis.client.compression.{Compression, Decoder => CompressionDecoder, Encoder => CompressionEncoder}
import stasis.client.encryption.{Aes, Decoder => EncryptionDecoder, Encoder => EncryptionEncoder}
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.internal.{ConfigOverride, FutureOps}
import stasis.client.staging.{DefaultFileStaging, FileStaging}
import stasis.client.tracking.TrackerView
import stasis.client.tracking.trackers.DefaultTracker
import stasis.core.persistence.backends.memory.EventLogMemoryBackend

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

trait Base extends FutureOps {
  implicit def system: ActorSystem[SpawnProtocol.Command]
  implicit def ec: ExecutionContext
  implicit def mat: Materializer
  implicit def log: Logger

  def directory: ApplicationDirectory

  def configOverride: typesafe.Config
  def rawConfig: typesafe.Config

  implicit def timeout: Timeout
  def terminationDelay: FiniteDuration

  def checksum: Checksum
  def compression: CompressionEncoder with CompressionDecoder
  def encryption: EncryptionEncoder with EncryptionDecoder
  def staging: FileStaging
  def tracker: DefaultTracker

  def terminateService: () => Unit
}

object Base {
  def apply(
    applicationDirectory: ApplicationDirectory,
    terminate: () => Unit
  )(implicit
    typedSystem: ActorSystem[SpawnProtocol.Command],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      Try {
        new Base {
          override implicit val system: ActorSystem[SpawnProtocol.Command] = typedSystem
          override implicit val ec: ExecutionContext = typedSystem.executionContext
          override implicit val mat: Materializer = SystemMaterializer(system).materializer
          override implicit val log: Logger = logger

          override val directory: ApplicationDirectory =
            applicationDirectory

          override val configOverride: typesafe.Config =
            ConfigOverride.load(directory)

          override val rawConfig: typesafe.Config =
            configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve()

          override implicit val timeout: Timeout =
            rawConfig.getDuration("service.internal-query-timeout").toMillis.millis

          override val terminationDelay: FiniteDuration =
            rawConfig.getDuration("service.termination-delay").toMillis.millis

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
                  name = s"tracker-${java.util.UUID.randomUUID().toString}",
                  initialState = state
                )(implicitly[ClassTag[TrackerView.State]], typedSystem, timeout)
            )

          override val terminateService: () => Unit = terminate
        }
      }
    )
}
