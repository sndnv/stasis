package stasis.client.service.components.bootstrap.init

import scala.concurrent.Future
import scala.util.Try

import stasis.client.service.ApplicationArguments

object ViaCli {
  def retrieveArguments(
    args: ApplicationArguments.Mode.Bootstrap
  ): Future[ApplicationArguments.Mode.Bootstrap] =
    Future.fromTry(
      for {
        _ <- Try(args.validate())
      } yield {
        args
      }
    )

  def retrieveCredentials(
    args: ApplicationArguments.Mode.Bootstrap
  ): Future[(String, Array[Char])] =
    Future.fromTry(
      Try {
        require(args.userName.nonEmpty, "User name cannot be empty")
        require(args.userPassword.nonEmpty, "User password cannot be empty")

        (args.userName, args.userPassword)
      }
    )
}
