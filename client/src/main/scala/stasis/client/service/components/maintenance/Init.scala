package stasis.client.service.components.maintenance

import java.io.Console

import scala.concurrent.Future
import scala.util.Try

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance

trait Init {
  def retrieveCredentials(): Future[ApplicationArguments.Mode.Maintenance]
}

object Init {
  def apply(base: Base, console: Option[Console]): Future[Init] = {
    import base._

    Try(base.args.validate()).future.flatMap { _ =>
      console match {
        case Some(console) =>
          base.log.debug("Console available; using stdin-based maintenance...")
          viaStdIn(console, base.args)

        case None =>
          base.log.debug("Console not available; using CLI-based maintenance...")
          viaCli(base.args)
      }
    }
  }

  def viaStdIn(console: Console, args: ApplicationArguments.Mode.Maintenance): Future[Init] =
    Future.successful(
      new Init {
        override def retrieveCredentials(): Future[ApplicationArguments.Mode.Maintenance] =
          maintenance.init.ViaStdIn.retrieveCredentials(console, args)
      }
    )

  def viaCli(args: ApplicationArguments.Mode.Maintenance): Future[Init] =
    Future.successful(
      new Init {
        override def retrieveCredentials(): Future[ApplicationArguments.Mode.Maintenance] =
          maintenance.init.ViaCli.retrieveCredentials(args)
      }
    )
}
