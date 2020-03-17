package stasis.client.service

import java.io.Console

import scala.util.{Failure, Success, Try}

trait CredentialsReader {
  def retrieve(): Try[(String, Array[Char])]
}

object CredentialsReader {
  class UsernameAndPassword(console: Option[Console]) extends CredentialsReader {
    override def retrieve(): Try[(String, Array[Char])] =
      console match {
        case Some(console) =>
          val username = console.readLine("Username: ").trim
          val password = console.readPassword("Password: ")

          if (username.nonEmpty && password.nonEmpty) {
            Success((username, password))
          } else {
            Failure(new IllegalArgumentException("Empty username and/or password provided"))
          }

        case None =>
          Failure(new IllegalStateException("Console not available; cannot retrieve credentials"))
      }
  }

  object UsernameAndPassword {
    def apply(console: Option[Console]): UsernameAndPassword = new UsernameAndPassword(console)
    def apply(): UsernameAndPassword = new UsernameAndPassword(console = Option(System.console()))
  }
}
