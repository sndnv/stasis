package stasis.core.routing

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter

import stasis.layers.telemetry.metrics.MeterExtensions._
import stasis.layers.telemetry.metrics.MetricsProvider

object Metrics {
  def noop(): Set[MetricsProvider] = Set(
    Router.NoOp
  )

  def default(meter: Meter, namespace: String): Set[MetricsProvider] = Set(
    new Router.Default(meter, namespace)
  )

  trait Router extends MetricsProvider {
    def recordPush(router: String, bytes: Long): Unit
    def recordPushFailure(router: String, reason: Router.PushFailure): Unit
    def recordPull(router: String, bytes: Long): Unit
    def recordPullFailure(router: String, reason: Router.PullFailure): Unit
    def recordDiscard(router: String, bytes: Long): Unit
    def recordDiscardFailure(router: String, reason: Router.DiscardFailure): Unit
    def recordReserve(router: String, bytes: Long): Unit
    def recordReserveFailure(router: String, reason: Router.ReserveFailure): Unit
    def recordStage(router: String, bytes: Long): Unit
  }

  object Router {
    sealed trait PushFailure
    object PushFailure {
      case object NoSinks extends PushFailure
      case object ReservationNotRemoved extends PushFailure
      case object DistributionFailed extends PushFailure
    }

    sealed trait PullFailure
    object PullFailure {
      case object NoContent extends PullFailure
      case object NoNodes extends PullFailure
      case object NoDestinations extends PullFailure
      case object NoManifest extends PullFailure
      case object MissingNode extends PullFailure
      case object Exception extends PullFailure
    }

    sealed trait DiscardFailure
    object DiscardFailure {
      case object NoDestinations extends DiscardFailure
      case object NoManifest extends DiscardFailure
      case object MissingNodeOrCrate extends DiscardFailure
    }

    sealed trait ReserveFailure
    object ReserveFailure {
      case object NoStorage extends ReserveFailure
      case object ReservationExists extends ReserveFailure
      case object ReservationRejected extends ReserveFailure
      case object DistributionFailed extends ReserveFailure
    }

    object NoOp extends Router {
      override def recordPush(router: String, bytes: Long): Unit = ()
      override def recordPushFailure(router: String, reason: Router.PushFailure): Unit = ()
      override def recordPull(router: String, bytes: Long): Unit = ()
      override def recordPullFailure(router: String, reason: Router.PullFailure): Unit = ()
      override def recordDiscard(router: String, bytes: Long): Unit = ()
      override def recordDiscardFailure(router: String, reason: Router.DiscardFailure): Unit = ()
      override def recordReserve(router: String, bytes: Long): Unit = ()
      override def recordReserveFailure(router: String, reason: Router.ReserveFailure): Unit = ()
      override def recordStage(router: String, bytes: Long): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends Router {
      private val subsystem: String = "routers"

      private val pushOperations = meter.counter(name = s"${namespace}_${subsystem}_push_operations")
      private val pushOperationFailures = meter.counter(name = s"${namespace}_${subsystem}_push_operation_failures")
      private val pushBytes = meter.counter(name = s"${namespace}_${subsystem}_push_bytes")
      private val pullOperations = meter.counter(name = s"${namespace}_${subsystem}_pull_operations")
      private val pullOperationFailures = meter.counter(name = s"${namespace}_${subsystem}_pull_operation_failures")
      private val pullBytes = meter.counter(name = s"${namespace}_${subsystem}_pull_bytes")
      private val discardOperations = meter.counter(name = s"${namespace}_${subsystem}_discard_operations")
      private val discardOperationFailures = meter.counter(name = s"${namespace}_${subsystem}_discard_operation_failures")
      private val discardBytes = meter.counter(name = s"${namespace}_${subsystem}_discard_bytes")
      private val reserveOperations = meter.counter(name = s"${namespace}_${subsystem}_reserve_operations")
      private val reserveOperationFailures = meter.counter(name = s"${namespace}_${subsystem}_reserve_operation_failures")
      private val reserveBytes = meter.counter(name = s"${namespace}_${subsystem}_reserve_bytes")
      private val stageOperations = meter.counter(name = s"${namespace}_${subsystem}_stage_operations")
      private val stageBytes = meter.counter(name = s"${namespace}_${subsystem}_stage_bytes")

      override def recordPush(router: String, bytes: Long): Unit = {
        pushOperations.inc(Labels.Router -> router)
        pushBytes.add(value = bytes, Labels.Router -> router)
      }

      override def recordPushFailure(router: String, reason: Router.PushFailure): Unit =
        pushOperationFailures.inc(
          Labels.Router -> router,
          Labels.Reason -> stripSpecialCharacters(reason.getClass.getSimpleName)
        )

      override def recordPull(router: String, bytes: Long): Unit = {
        pullOperations.inc(Labels.Router -> router)
        pullBytes.add(value = bytes, Labels.Router -> router)
      }

      override def recordPullFailure(router: String, reason: Router.PullFailure): Unit =
        pullOperationFailures.inc(
          Labels.Router -> router,
          Labels.Reason -> stripSpecialCharacters(reason.getClass.getSimpleName)
        )

      override def recordDiscard(router: String, bytes: Long): Unit = {
        discardOperations.inc(Labels.Router -> router)
        discardBytes.add(value = bytes, Labels.Router -> router)
      }

      override def recordDiscardFailure(router: String, reason: Router.DiscardFailure): Unit =
        discardOperationFailures.inc(
          Labels.Router -> router,
          Labels.Reason -> stripSpecialCharacters(reason.getClass.getSimpleName)
        )

      override def recordReserve(router: String, bytes: Long): Unit = {
        reserveOperations.inc(Labels.Router -> router)
        reserveBytes.add(value = bytes, Labels.Router -> router)
      }

      override def recordReserveFailure(router: String, reason: Router.ReserveFailure): Unit =
        reserveOperationFailures.inc(
          Labels.Router -> router,
          Labels.Reason -> stripSpecialCharacters(reason.getClass.getSimpleName)
        )

      override def recordStage(router: String, bytes: Long): Unit = {
        stageOperations.inc(Labels.Router -> router)
        stageBytes.add(value = bytes, Labels.Router -> router)
      }

      private def stripSpecialCharacters(string: String): String =
        string.replaceAll("[^a-zA-Z0-9]", "")
    }
  }

  object Labels {
    val Router: AttributeKey[String] = AttributeKey.stringKey("router")
    val Reason: AttributeKey[String] = AttributeKey.stringKey("reason")
  }
}
