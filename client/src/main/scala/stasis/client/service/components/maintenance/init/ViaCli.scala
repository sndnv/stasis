package stasis.client.service.components.maintenance.init

import stasis.client.service.ApplicationArguments

import scala.concurrent.Future
import scala.util.Try

object ViaCli {
  def retrieveCredentials(
    args: ApplicationArguments.Mode.Maintenance
  ): Future[(String, Array[Char])] =
    Future.fromTry(
      Try {
        require(args.userName.nonEmpty, "User name cannot be empty")
        require(args.userPassword.nonEmpty, "User password cannot be empty")

        (args.userName, args.userPassword)
      }
    )
}
