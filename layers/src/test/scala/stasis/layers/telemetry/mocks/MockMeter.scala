package stasis.layers.telemetry.mocks

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

import scala.jdk.CollectionConverters._

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics._
import io.opentelemetry.context.Context

class MockMeter extends Meter {
  private val metrics: ConcurrentHashMap[String, AtomicLong] =
    new ConcurrentHashMap()

  private val callbacks: ConcurrentHashMap[
    String,
    Either[Consumer[ObservableLongMeasurement], Consumer[ObservableDoubleMeasurement]]
  ] = new ConcurrentHashMap()

  def metric(name: String): Long = Option(metrics.get(name)) match {
    case Some(value) => value.get()
    case None        => throw new IllegalArgumentException
  }

  def collect(): Unit =
    callbacks.asScala.foreach {
      case (metric, Left(callback)) =>
        callback.accept(
          new ObservableLongMeasurement {
            override def record(value: Long): Unit = record(value, Attributes.empty())

            override def record(value: Long, attributes: Attributes): Unit = {
              val _ = metrics.get(metric).incrementAndGet()
            }
          }
        )

      case (metric, Right(callback)) =>
        callback.accept(
          new ObservableDoubleMeasurement {
            override def record(value: Double): Unit = record(value, Attributes.empty())

            override def record(value: Double, attributes: Attributes): Unit = {
              val _ = metrics.get(metric).incrementAndGet()
            }
          }
        )
    }

  override def counterBuilder(name: String): LongCounterBuilder = new LongCounterBuilder {
    override def setDescription(description: String): LongCounterBuilder =
      this

    override def setUnit(unit: String): LongCounterBuilder =
      this

    override def ofDoubles(): DoubleCounterBuilder = new DoubleCounterBuilder {
      override def setDescription(description: String): DoubleCounterBuilder = this

      override def setUnit(unit: String): DoubleCounterBuilder = this

      override def build(): DoubleCounter = new DoubleCounter {
        metrics.put(name, new AtomicLong(0))

        override def add(value: Double): Unit =
          add(value, Attributes.empty(), Context.current())

        override def add(value: Double, attributes: Attributes): Unit =
          add(value, attributes, Context.current())

        override def add(value: Double, attributes: Attributes, context: Context): Unit = {
          val _ = metrics.get(name).incrementAndGet()
        }
      }

      override def buildWithCallback(callback: Consumer[ObservableDoubleMeasurement]): ObservableDoubleCounter = {
        metrics.put(name, new AtomicLong(0))
        callbacks.put(name, Right(callback))
        new ObservableDoubleCounter {}
      }
    }

    override def build(): LongCounter = {
      metrics.put(name, new AtomicLong(0))
      new LongCounter {
        override def add(value: Long): Unit =
          add(value, Attributes.empty(), Context.current())

        override def add(value: Long, attributes: Attributes): Unit =
          add(value, attributes, Context.current())

        override def add(value: Long, attributes: Attributes, context: Context): Unit = {
          val _ = metrics.get(name).incrementAndGet()
        }
      }
    }

    override def buildWithCallback(callback: Consumer[ObservableLongMeasurement]): ObservableLongCounter = {
      metrics.put(name, new AtomicLong(0))
      callbacks.put(name, Left(callback))
      new ObservableLongCounter {}
    }
  }

  override def upDownCounterBuilder(name: String): LongUpDownCounterBuilder = new LongUpDownCounterBuilder {
    override def setDescription(description: String): LongUpDownCounterBuilder =
      this

    override def setUnit(unit: String): LongUpDownCounterBuilder =
      this

    override def ofDoubles(): DoubleUpDownCounterBuilder = new DoubleUpDownCounterBuilder {
      override def setDescription(description: String): DoubleUpDownCounterBuilder = this

      override def setUnit(unit: String): DoubleUpDownCounterBuilder = this

      override def build(): DoubleUpDownCounter = new DoubleUpDownCounter {
        metrics.put(name, new AtomicLong(0))
        override def add(value: Double): Unit =
          add(value, Attributes.empty(), Context.current())

        override def add(value: Double, attributes: Attributes): Unit =
          add(value, attributes, Context.current())

        override def add(value: Double, attributes: Attributes, context: Context): Unit = {
          val _ = metrics.get(name).incrementAndGet()
        }
      }

      override def buildWithCallback(callback: Consumer[ObservableDoubleMeasurement]): ObservableDoubleUpDownCounter = {
        metrics.put(name, new AtomicLong(0))
        callbacks.put(name, Right(callback))
        new ObservableDoubleUpDownCounter {}
      }
    }

    override def build(): LongUpDownCounter = {
      metrics.put(name, new AtomicLong(0))
      new LongUpDownCounter {
        override def add(value: Long): Unit =
          add(value, Attributes.empty(), Context.current())

        override def add(value: Long, attributes: Attributes): Unit =
          add(value, attributes, Context.current())

        override def add(value: Long, attributes: Attributes, context: Context): Unit = {
          val _ = metrics.get(name).incrementAndGet()
        }
      }
    }

    override def buildWithCallback(callback: Consumer[ObservableLongMeasurement]): ObservableLongUpDownCounter = {
      metrics.put(name, new AtomicLong(0))
      callbacks.put(name, Left(callback))
      new ObservableLongUpDownCounter {}
    }
  }

  override def histogramBuilder(name: String): DoubleHistogramBuilder = new DoubleHistogramBuilder {
    override def setDescription(description: String): DoubleHistogramBuilder =
      this

    override def setUnit(unit: String): DoubleHistogramBuilder =
      this

    override def ofLongs(): LongHistogramBuilder = new LongHistogramBuilder {
      override def setDescription(description: String): LongHistogramBuilder =
        this

      override def setUnit(unit: String): LongHistogramBuilder =
        this

      override def build(): LongHistogram = {
        metrics.put(name, new AtomicLong(0))
        new LongHistogram {
          override def record(value: Long): Unit =
            record(value, Attributes.empty(), Context.current())

          override def record(value: Long, attributes: Attributes): Unit =
            record(value, attributes, Context.current())

          override def record(value: Long, attributes: Attributes, context: Context): Unit = {
            val _ = metrics.get(name).incrementAndGet()
          }
        }
      }
    }

    override def build(): DoubleHistogram = new DoubleHistogram {
      metrics.put(name, new AtomicLong(0))

      override def record(value: Double): Unit =
        record(value, Attributes.empty(), Context.current())

      override def record(value: Double, attributes: Attributes): Unit =
        record(value, attributes, Context.current())

      override def record(value: Double, attributes: Attributes, context: Context): Unit = {
        val _ = metrics.get(name).incrementAndGet()
      }
    }
  }

  override def gaugeBuilder(name: String): DoubleGaugeBuilder =
    throw new UnsupportedOperationException
}

object MockMeter {
  def apply(): MockMeter = new MockMeter()
}
