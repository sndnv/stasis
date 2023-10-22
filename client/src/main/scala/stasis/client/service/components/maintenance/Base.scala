package stasis.client.service.components.maintenance

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import com.typesafe.{config => typesafe}
import org.slf4j.Logger
import stasis.client.service.ApplicationArguments.Mode
import stasis.client.service.components.internal.{ConfigOverride, FutureOps}
import stasis.client.service.{ApplicationArguments, ApplicationDirectory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Base extends FutureOps {
  implicit def system: ActorSystem[SpawnProtocol.Command]
  implicit def ec: ExecutionContext
  implicit def log: Logger

  def args: ApplicationArguments.Mode.Maintenance

  def directory: ApplicationDirectory

  def configOverride: typesafe.Config
  def rawConfig: typesafe.Config
}

object Base {
  def apply(
    modeArguments: ApplicationArguments.Mode.Maintenance,
    applicationDirectory: ApplicationDirectory
  )(implicit
    typedSystem: ActorSystem[SpawnProtocol.Command],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      Try {
        new Base {
          override implicit val system: ActorSystem[SpawnProtocol.Command] = typedSystem
          override implicit val ec: ExecutionContext = typedSystem.executionContext
          override implicit val log: Logger = logger

          override val args: Mode.Maintenance = modeArguments
          override val directory: ApplicationDirectory = applicationDirectory

          override val configOverride: typesafe.Config =
            ConfigOverride.load(directory)

          override val rawConfig: typesafe.Config =
            configOverride.withFallback(system.settings.config).getConfig("stasis.client").resolve()
        }
      }
    )
}
