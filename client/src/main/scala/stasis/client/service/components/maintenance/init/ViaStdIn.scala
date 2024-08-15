package stasis.client.service.components.maintenance.init

import java.io.Console

import stasis.client.service.ApplicationArguments

import scala.concurrent.Future
import scala.util.{Success, Try}

object ViaStdIn {
  def retrieveCredentials(
    console: Console,
    args: ApplicationArguments.Mode.Maintenance
  ): Future[ApplicationArguments.Mode.Maintenance] = {
    def getTextOrAsk(existing: String, query: String): Try[String] =
      existing match {
        case v if v.nonEmpty => Success(v)
        case _               => Try(console.readLine(query)).map(_.trim)
      }

    def getPasswordOrAsk(existing: Array[Char], query: String): Try[Array[Char]] =
      existing match {
        case v if v.nonEmpty => Success(v)
        case _               => Try(console.readPassword(query))
      }

    def getOptionalPasswordOrAsk(existing: Option[Array[Char]], query: String): Try[Array[Char]] =
      existing match {
        case Some(v) if v.nonEmpty => Success(v)
        case _                     => Try(console.readPassword(query))
      }

    Future.fromTry(
      args match {
        case args: ApplicationArguments.Mode.Maintenance.ResetUserCredentials =>
          for {
            currentPassword <- getPasswordOrAsk(args.currentUserPassword, "Current User Password: ")
            newPassword <- getPasswordOrAsk(args.newUserPassword, "New User Password: ")
            newSalt <- getTextOrAsk(args.newUserSalt, "New User Salt: ")
          } yield {
            require(currentPassword.nonEmpty, "Current user password cannot be empty")
            require(newPassword.nonEmpty, "New user password cannot be empty")
            require(newSalt.nonEmpty, "New user salt cannot be empty")

            args.copy(
              currentUserPassword = currentPassword,
              newUserPassword = newPassword,
              newUserSalt = newSalt
            )
          }

        case args: ApplicationArguments.Mode.Maintenance.PushDeviceSecret =>
          for {
            name <- getTextOrAsk(args.currentUserName, "Current User Name: ")
            currentPassword <- getPasswordOrAsk(args.currentUserPassword, "Current User Password: ")
            remotePassword <- getOptionalPasswordOrAsk(args.remotePassword, "Remote Password (optional): ")
          } yield {
            require(name.nonEmpty, "Current user name cannot be empty")
            require(currentPassword.nonEmpty, "Current user password cannot be empty")

            args.copy(
              currentUserName = name,
              currentUserPassword = currentPassword,
              remotePassword = Some(remotePassword).filter(_.nonEmpty)
            )
          }

        case args: ApplicationArguments.Mode.Maintenance.PullDeviceSecret =>
          for {
            name <- getTextOrAsk(args.currentUserName, "Current User Name: ")
            currentPassword <- getPasswordOrAsk(args.currentUserPassword, "Current User Password: ")
            remotePassword <- getOptionalPasswordOrAsk(args.remotePassword, "Remote Password (optional): ")
          } yield {
            require(name.nonEmpty, "Current user name cannot be empty")
            require(currentPassword.nonEmpty, "Current user password cannot be empty")

            args.copy(
              currentUserName = name,
              currentUserPassword = currentPassword,
              remotePassword = Some(remotePassword).filter(_.nonEmpty)
            )
          }

        case args: ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret =>
          for {
            name <- getTextOrAsk(args.currentUserName, "Current User Name: ")
            currentPassword <- getPasswordOrAsk(args.currentUserPassword, "Current User Password: ")
            oldPassword <- getPasswordOrAsk(args.oldUserPassword, "Old User Password: ")
          } yield {
            require(name.nonEmpty, "Current user name cannot be empty")
            require(currentPassword.nonEmpty, "Current user password cannot be empty")
            require(oldPassword.nonEmpty, "Old user password cannot be empty")

            args.copy(
              currentUserName = name,
              currentUserPassword = currentPassword,
              oldUserPassword = oldPassword
            )
          }

        case _ => Success(args) // do nothing
      }
    )
  }
}
