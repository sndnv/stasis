package stasis.client.service.components.maintenance.init

import stasis.client.service.ApplicationArguments

import scala.concurrent.Future
import scala.util.Try

object ViaCli {
  def retrieveCurrentCredentials(
    args: ApplicationArguments.Mode.Maintenance
  ): Future[(String, Array[Char])] =
    Future.fromTry(
      Try {
        require(args.currentUserName.nonEmpty, "Current user name cannot be empty")
        require(args.currentUserPassword.nonEmpty, "Current user password cannot be empty")

        (args.currentUserName, args.currentUserPassword)
      }
    )

  def retrieveNewCredentials(
    args: ApplicationArguments.Mode.Maintenance
  ): Future[(Array[Char], String)] =
    Future.fromTry(
      Try {
        require(args.newUserPassword.nonEmpty, "New user password cannot be empty")
        require(args.newUserSalt.nonEmpty, "New user salt cannot be empty")

        (args.newUserPassword, args.newUserSalt)
      }
    )
}
