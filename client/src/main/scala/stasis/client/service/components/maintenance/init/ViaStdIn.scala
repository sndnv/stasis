package stasis.client.service.components.maintenance.init

import java.io.Console

import stasis.client.service.ApplicationArguments

import scala.concurrent.Future
import scala.util.{Success, Try}

object ViaStdIn {
  def retrieveCredentials(
    console: Console,
    args: ApplicationArguments.Mode.Maintenance
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
      } yield {
        require(name.nonEmpty, "User name cannot be empty")
        require(password.nonEmpty, "User password cannot be empty")

        (name, password)
      }
    )
}
