package stasis.test.specs.unit

import akka.util.Timeout
import org.scalatest.{AsyncFlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait AsyncUnitSpec extends AsyncFlatSpec with Matchers {
  implicit val timeout: Timeout = 500.milliseconds

  implicit class AwaitableFuture[T](val future: Future[T]) {
    def await: T = Await.result(future, timeout.duration)
  }
}
