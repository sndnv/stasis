package stasis.client.service.components.bootstrap

import java.io.Console

import scala.concurrent.Future

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.bootstrap

trait Init {
  def arguments(): Future[ApplicationArguments.Mode.Bootstrap]
  def credentials(): Future[(String, Array[Char])]
}

object Init {
  def apply(base: Base, console: Option[Console]): Future[Init] =
    console match {
      case Some(console) =>
        base.log.debug("Console available; using stdin-based bootstrap...")
        viaStdIn(console, base.args)

      case None =>
        base.log.debug("Console not available; using CLI-based bootstrap...")
        viaCli(base.args)
    }

  def viaStdIn(console: Console, args: ApplicationArguments.Mode.Bootstrap): Future[Init] =
    Future.successful(
      new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] =
          bootstrap.init.ViaStdIn.retrieveArguments(console, args)

        override def credentials(): Future[(String, Array[Char])] =
          bootstrap.init.ViaStdIn.retrieveCredentials(console, args)
      }
    )

  def viaCli(args: ApplicationArguments.Mode.Bootstrap): Future[Init] =
    Future.successful(
      new Init {
        override def arguments(): Future[ApplicationArguments.Mode.Bootstrap] =
          bootstrap.init.ViaCli.retrieveArguments(args)

        override def credentials(): Future[(String, Array[Char])] =
          bootstrap.init.ViaCli.retrieveCredentials(args)
      }
    )
}
