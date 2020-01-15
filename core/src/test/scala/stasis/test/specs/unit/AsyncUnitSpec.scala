package stasis.test.specs.unit

import akka.util.Timeout
import akka.Done
import org.scalatest.{AsyncFlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait AsyncUnitSpec extends AsyncFlatSpec with Matchers {
  implicit val timeout: Timeout = 500.milliseconds

  implicit class AwaitableFuture[T](val future: Future[T]) {
    def await: T = Await.result(future, timeout.duration)
  }

  def await(delay: FiniteDuration, withSystem: akka.actor.ActorSystem): Unit = {
    val _ = akka.pattern
      .after(
        duration = delay,
        using = withSystem.scheduler
      )(Future.successful(Done))(withSystem.dispatcher)
      .await
  }

  def await(delay: FiniteDuration, withSystem: akka.actor.typed.ActorSystem[_]): Unit = {
    val _ = akka.pattern
      .after(
        duration = delay,
        using = withSystem.scheduler
      )(Future.successful(Done))(withSystem.executionContext)
      .await
  }
}
