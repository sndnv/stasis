package stasis.layers

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._
import scala.util.Random

object Generators {
  def generateFiniteDuration(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): FiniteDuration =
    rnd.nextLong(0, 1.day.toSeconds).seconds

  def generateSeq[T](
    min: Int = 0,
    max: Int = 10,
    g: => T
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[T] =
    LazyList.continually(g).take(rnd.nextInt(min, max))

  def generateUri(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val host = generateString(withSize = 10)
    val port = rnd.nextInt(50000, 60000)
    val endpoint = generateString(withSize = 20)
    s"http://$host:$port/$endpoint".toLowerCase
  }

  def generateString(
    withSize: Int
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val random = Random.javaRandomToRandom(rnd)
    random.alphanumeric.take(withSize).mkString("")
  }
}
