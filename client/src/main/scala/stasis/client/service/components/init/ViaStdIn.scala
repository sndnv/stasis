package stasis.client.service.components.init

import java.io.Console

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.Done

object ViaStdIn {
  def retrieve(console: Console): Future[(String, Array[Char])] =
    Future.fromTry(
      for {
        username <- Try(console.readLine("Username: ").trim)
        password <- Try(console.readPassword("Password: "))
        _ <- requireNonEmpty(username, password)
      } yield {
        (username, password)
      }
    )

  private def requireNonEmpty(username: String, password: Array[Char]): Try[Done] =
    if (username.nonEmpty && password.nonEmpty) {
      Success(Done)
    } else {
      Failure(new IllegalArgumentException("Empty username and/or password provided"))
    }
}
