package stasis.client.service.components.maintenance.init

import stasis.client.service.ApplicationArguments
import scala.concurrent.Future
import scala.util.Try

object ViaCli {
  def retrieveCredentials(
    args: ApplicationArguments.Mode.Maintenance
  ): Future[ApplicationArguments.Mode.Maintenance] =
    Future.fromTry(
      Try {
        args match {
          case args: ApplicationArguments.Mode.Maintenance.ResetUserCredentials =>
            require(args.currentUserPassword.nonEmpty, "Current user password cannot be empty")
            require(args.newUserPassword.nonEmpty, "New user password cannot be empty")
            require(args.newUserSalt.nonEmpty, "New user salt cannot be empty")
            args

          case args: ApplicationArguments.Mode.Maintenance.PushDeviceSecret =>
            require(args.currentUserName.nonEmpty, "Current user name cannot be empty")
            require(args.currentUserPassword.nonEmpty, "Current user password cannot be empty")
            args

          case args: ApplicationArguments.Mode.Maintenance.PullDeviceSecret =>
            require(args.currentUserName.nonEmpty, "Current user name cannot be empty")
            require(args.currentUserPassword.nonEmpty, "Current user password cannot be empty")
            args

          case args: ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret =>
            require(args.currentUserName.nonEmpty, "Current user name cannot be empty")
            require(args.currentUserPassword.nonEmpty, "Current user password cannot be empty")
            require(args.oldUserPassword.nonEmpty, "Old user password cannot be empty")
            args

          case _ => args // do nothing
        }
      }
    )
}
