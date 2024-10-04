package stasis.client.service.components.bootstrap.init

import java.io.Console

import scala.concurrent.Future
import scala.util.Success
import scala.util.Try

import stasis.client.service.ApplicationArguments

object ViaStdIn {
  def retrieveArguments(
    console: Console,
    args: ApplicationArguments.Mode.Bootstrap
  ): Future[ApplicationArguments.Mode.Bootstrap] =
    Future.fromTry(
      for {
        url <- args.serverBootstrapUrl match {
          case url if url.trim.nonEmpty => Success(url)
          case _                        => Try(console.readLine("Server bootstrap URL: ").trim)
        }
        code <- args.bootstrapCode match {
          case code if code.trim.nonEmpty => Success(code)
          case _                          => Try(console.readLine("Bootstrap Code: ").trim)
        }
        updated = args.copy(serverBootstrapUrl = url, bootstrapCode = code)
        _ <- Try(updated.validate())
      } yield {
        updated
      }
    )

  def retrieveCredentials(
    console: Console,
    args: ApplicationArguments.Mode.Bootstrap
  ): Future[(String, Array[Char])] =
    Future.fromTry(
      for {
        name <- args.userName match {
          case name if name.nonEmpty => Success(name)
          case _                     => Try(console.readLine("User Name: ")).map(_.trim)
        }
        password <- args.userPassword match {
          case password if password.nonEmpty => Success(password)
          case _                             => Try(console.readPassword("User Password: "))
        }
        passwordConfirm <- args.userPassword match { // password was explicitly provided; no need for confirmation
          case password if password.nonEmpty => Success(password)
          case _                             => Try(console.readPassword("Confirm Password: "))
        }
      } yield {
        require(name.nonEmpty, "User name cannot be empty")
        require(password.nonEmpty, "User password cannot be empty")
        require(java.util.Objects.deepEquals(password, passwordConfirm), "Passwords do not match")
        (name, password)
      }
    )
}
