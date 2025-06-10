package stasis.layers.telemetry.analytics

import scala.concurrent.Future

import org.apache.pekko.Done

trait AnalyticsClient {
  def sendAnalyticsEntry(entry: AnalyticsEntry): Future[Done]
}

object AnalyticsClient {
  trait Provider {
    def client: AnalyticsClient
  }

  object Provider {
    def apply(f: () => AnalyticsClient): Provider = new Provider {
      override def client: AnalyticsClient = f()
    }
  }
}
