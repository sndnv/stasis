package stasis.layers

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.util.Timeout
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

trait UnitSpec extends AsyncFlatSpec with Matchers {
  implicit val timeout: Timeout = 500.milliseconds

  implicit class AwaitableFuture[T](val future: Future[T]) {
    def await: T = Await.result(future, timeout.duration)
  }

  def await(delay: FiniteDuration, withSystem: org.apache.pekko.actor.ActorSystem): Unit = {
    val _ = org.apache.pekko.pattern
      .after(
        duration = delay,
        using = withSystem.scheduler
      )(Future.successful(Done))(withSystem.dispatcher)
      .await
  }

  def await(delay: FiniteDuration, withSystem: org.apache.pekko.actor.typed.ActorSystem[_]): Unit =
    await(delay, withSystem.classicSystem)

  def after[T](delay: FiniteDuration, using: org.apache.pekko.actor.ActorSystem)(value: => Future[T]): Future[T] =
    org.apache.pekko.pattern
      .after(
        duration = delay,
        using = using.scheduler
      )(value)(using.dispatcher)

  def after[T](delay: FiniteDuration, using: org.apache.pekko.actor.typed.ActorSystem[_])(value: => Future[T]): Future[T] =
    after(delay, using.classicSystem)(value)

  def withRetry(f: => Future[Assertion]): Future[Assertion] =
    withRetry(times = 2)(f = f)

  def withRetry(times: Int)(f: => Future[Assertion]): Future[Assertion] =
    try {
      f.recoverWith {
        case e if times > 0 =>
          alert(s"Test failed with [${e.getMessage}]; retrying...")
          withRetry(times = times - 1)(f = f)
      }
    } catch {
      case e if times > 0 =>
        alert(s"Test failed with [${e.getMessage}]; retrying...")
        withRetry(times = times - 1)(f = f)
    }
}
