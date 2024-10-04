package stasis.layers.persistence

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter

import stasis.layers.telemetry.metrics.MetricsProvider

object Metrics {
  def noop(): Set[MetricsProvider] = Set(
    Store.NoOp
  )

  def default(meter: Meter, namespace: String): Set[MetricsProvider] = Set(
    new Store.Default(meter, namespace)
  )

  trait Store extends MetricsProvider {
    def recordPut(store: String): Unit
    def recordGet(store: String): Unit
    def recordGet(store: String, entries: Int): Unit
    def recordDelete(store: String): Unit
  }

  object Store {
    object NoOp extends Store {
      override def recordPut(store: String): Unit = ()
      override def recordGet(store: String): Unit = ()
      override def recordGet(store: String, entries: Int): Unit = ()
      override def recordDelete(store: String): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends Store {
      import stasis.layers.telemetry.metrics.MeterExtensions._

      private val subsystem: String = "persistence_store"

      private val putOperations = meter.counter(name = s"${namespace}_${subsystem}_put_operations")
      private val getOperations = meter.counter(name = s"${namespace}_${subsystem}_get_operations")
      private val deleteOperations = meter.counter(name = s"${namespace}_${subsystem}_delete_operations")

      override def recordPut(store: String): Unit =
        putOperations.inc(Labels.Store -> store)

      override def recordGet(store: String): Unit =
        getOperations.inc(Labels.Store -> store)

      override def recordGet(store: String, entries: Int): Unit =
        getOperations.add(value = entries.toLong, Labels.Store -> store)

      override def recordDelete(store: String): Unit =
        deleteOperations.inc(Labels.Store -> store)
    }

    object Labels {
      val Store: AttributeKey[String] = AttributeKey.stringKey("store")
    }
  }
}
