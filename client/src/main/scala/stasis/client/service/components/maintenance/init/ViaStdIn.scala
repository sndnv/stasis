package stasis.client.service.components.maintenance.init

import java.io.Console

import stasis.client.service.ApplicationArguments

import scala.concurrent.Future
import scala.util.{Success, Try}

object ViaStdIn {
  def retrieveCurrentCredentials(
    console: Console,
    args: ApplicationArguments.Mode.Maintenance
  ): Future[(String, Array[Char])] =
    Future.fromTry(
      for {
        name <- args.currentUserName match {
          case name if name.nonEmpty => Success(name)
          case _                     => Try(console.readLine("Current User Name: ")).map(_.trim)
        }
        password <- args.currentUserPassword match {
          case password if password.nonEmpty => Success(password)
          case _                             => Try(console.readPassword("Current User Password: "))
        }
      } yield {
        require(name.nonEmpty, "Current user name cannot be empty")
        require(password.nonEmpty, "Current user password cannot be empty")

        (name, password)
      }
    )

  def retrieveNewCredentials(
    console: Console,
    args: ApplicationArguments.Mode.Maintenance
  ): Future[(Array[Char], String)] =
    Future.fromTry(
      for {
        password <- args.newUserPassword match {
          case password if password.nonEmpty => Success(password)
          case _                             => Try(console.readPassword("New User Password: "))
        }
        salt <- args.newUserSalt match {
          case salt if salt.nonEmpty => Success(salt)
          case _                     => Try(console.readLine("New User Salt: ")).map(_.trim)
        }
      } yield {
        require(password.nonEmpty, "New user password cannot be empty")
        require(salt.nonEmpty, "New user salt cannot be empty")

        (password, salt)
      }
    )
}
