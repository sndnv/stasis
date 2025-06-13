package stasis.layers.telemetry.analytics

import scala.concurrent.Future

import stasis.layers.telemetry.ApplicationInformation

trait AnalyticsCollector {
  def recordEvent(name: String): Unit =
    recordEvent(name = name, attributes = Map.empty[String, String])

  def recordEvent(name: String, attributes: (String, String)*): Unit =
    recordEvent(name = name, attributes = attributes.toMap)

  def recordFailure(e: Throwable): Unit = recordFailure(message = s"${e.getClass.getSimpleName} - ${e.getMessage}")

  def recordEvent(name: String, attributes: Map[String, String]): Unit

  def recordFailure(message: String): Unit

  def state: Future[AnalyticsEntry]

  def send(): Unit

  def persistence: Option[AnalyticsPersistence]
}

object AnalyticsCollector {
  object NoOp extends AnalyticsCollector {
    override def recordEvent(name: String, attributes: Map[String, String]): Unit = ()
    override def recordFailure(message: String): Unit = ()
    override def state: Future[AnalyticsEntry] = Future.successful(AnalyticsEntry.collected(app = ApplicationInformation.none))
    override def send(): Unit = ()
    override def persistence: Option[AnalyticsPersistence] = None
  }
}
