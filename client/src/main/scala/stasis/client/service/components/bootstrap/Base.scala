package stasis.client.service.components.bootstrap

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import org.slf4j.Logger
import stasis.client.service.ApplicationArguments.Mode
import stasis.client.service.components.internal.FutureOps
import stasis.client.service.{ApplicationArguments, ApplicationDirectory, ApplicationTemplates}

import scala.concurrent.{ExecutionContext, Future}

trait Base extends FutureOps {
  implicit def system: ActorSystem[SpawnProtocol.Command]
  implicit def ec: ExecutionContext
  implicit def log: Logger

  def args: ApplicationArguments.Mode.Bootstrap

  def directory: ApplicationDirectory
  def templates: ApplicationTemplates
}

object Base {
  def apply(
    modeArguments: ApplicationArguments.Mode.Bootstrap,
    applicationDirectory: ApplicationDirectory
  )(implicit
    typedSystem: ActorSystem[SpawnProtocol.Command],
    logger: Logger
  ): Future[Base] =
    Future.fromTry(
      for {
        applicationTemplates <- ApplicationTemplates()
      } yield {
        new Base {
          override implicit val system: ActorSystem[SpawnProtocol.Command] = typedSystem
          override implicit val ec: ExecutionContext = typedSystem.executionContext
          override implicit val log: Logger = logger

          override val args: Mode.Bootstrap = modeArguments
          override val directory: ApplicationDirectory = applicationDirectory
          override val templates: ApplicationTemplates = applicationTemplates
        }
      }
    )
}
