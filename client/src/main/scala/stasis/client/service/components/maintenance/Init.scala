package stasis.client.service.components.maintenance

import java.io.Console

import scala.concurrent.Future

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance

trait Init {
  def currentCredentials(): Future[(String, Array[Char])]
  def newCredentials(): Future[(Array[Char], String)]
}

object Init {
  def apply(base: Base, console: Option[Console]): Future[Init] =
    console match {
      case Some(console) =>
        base.log.debug("Console available; using stdin-based maintenance...")
        viaStdIn(console, base.args)

      case None =>
        base.log.debug("Console not available; using CLI-based maintenance...")
        viaCli(base.args)
    }

  def viaStdIn(console: Console, args: ApplicationArguments.Mode.Maintenance): Future[Init] =
    Future.successful(
      new Init {
        override def currentCredentials(): Future[(String, Array[Char])] =
          maintenance.init.ViaStdIn.retrieveCurrentCredentials(console, args)

        override def newCredentials(): Future[(Array[Char], String)] =
          maintenance.init.ViaStdIn.retrieveNewCredentials(console, args)
      }
    )

  def viaCli(args: ApplicationArguments.Mode.Maintenance): Future[Init] =
    Future.successful(
      new Init {
        override def currentCredentials(): Future[(String, Array[Char])] =
          maintenance.init.ViaCli.retrieveCurrentCredentials(args)

        override def newCredentials(): Future[(Array[Char], String)] =
          maintenance.init.ViaCli.retrieveNewCredentials(args)
      }
    )
}
