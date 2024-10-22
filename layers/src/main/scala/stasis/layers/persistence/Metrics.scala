package stasis.layers.persistence

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
    def recordPut[T](store: String)(f: => Future[T]): Future[T] =
      recordOperation(store, operation = Store.Operation.Put)(f)

    def recordGet[T](store: String)(f: => Future[T]): Future[T] =
      recordOperation(store, operation = Store.Operation.Get)(f)

    def recordDelete[T](store: String)(f: => Future[T]): Future[T] =
      recordOperation(store, operation = Store.Operation.Delete)(f)

    def recordContains[T](store: String)(f: => Future[T]): Future[T] =
      recordOperation(store, operation = Store.Operation.Contains)(f)

    def recordList[T](store: String)(f: => Future[T]): Future[T] =
      recordOperation(store, operation = Store.Operation.List)(f)

    def recordOperation[T](store: String, operation: Store.Operation)(f: => Future[T]): Future[T]
  }

  object Store {
    object NoOp extends Store {
      override def recordOperation[T](store: String, operation: Store.Operation)(f: => Future[T]): Future[T] = f
    }

    class Default(meter: Meter, namespace: String) extends Store {
      import stasis.layers.telemetry.metrics.MeterExtensions._

      private val subsystem: String = "persistence_store"

      private val operationDuration = meter.histogram(name = s"${namespace}_${subsystem}_operation_duration")

      override def recordOperation[T](store: String, operation: Store.Operation)(f: => Future[T]): Future[T] = {
        val start = System.currentTimeMillis()
        val result = f
        result.foreach { _ =>
          operationDuration.record(
            value = System.currentTimeMillis() - start,
            attributes = Labels.Store -> store,
            Labels.Operation -> operation.name
          )
        }(ExecutionContext.parasitic)
        result
      }
    }

    sealed trait Operation {
      def name: String
    }

    object Operation {
      case object Put extends Operation {
        val name: String = "put"
      }
      case object Get extends Operation {
        val name: String = "get"
      }
      case object Delete extends Operation {
        val name: String = "delete"
      }
      case object Contains extends Operation {
        val name: String = "contains"
      }
      case object List extends Operation {
        val name: String = "list"
      }
    }

    object Labels {
      val Operation: AttributeKey[String] = AttributeKey.stringKey("operation")
      val Store: AttributeKey[String] = AttributeKey.stringKey("store")
    }
  }
}
