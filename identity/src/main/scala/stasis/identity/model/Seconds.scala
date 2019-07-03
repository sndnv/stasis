package stasis.identity.model

import scala.concurrent.duration.FiniteDuration

final case class Seconds(value: Long) extends AnyVal

object Seconds {
  import scala.language.implicitConversions

  def apply(duration: FiniteDuration): Seconds = Seconds(duration.toSeconds)

  implicit def durationToSeconds(duration: FiniteDuration): Seconds = Seconds(duration)
}
