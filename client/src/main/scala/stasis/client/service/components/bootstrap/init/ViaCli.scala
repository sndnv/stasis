package stasis.client.service.components.bootstrap.init

import stasis.client.service.ApplicationArguments

import scala.concurrent.Future
import scala.util.Try

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
  ): Future[Array[Char]] =
    Future.fromTry(
      Try {
        require(args.userPassword.nonEmpty, "User password cannot be empty")
        args.userPassword
      }
    )
}
