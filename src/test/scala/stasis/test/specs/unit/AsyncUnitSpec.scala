package stasis.test.specs.unit

import akka.util.Timeout
import org.scalatest.{fixture, Matchers}

import scala.concurrent.{Await, Future}

trait AsyncUnitSpec extends fixture.AsyncFlatSpec with Matchers {
  implicit val timeout: Timeout

  implicit class AwaitableFuture[T](val future: Future[T]) {
    def await: T = Await.result(future, timeout.duration)
  }
}
