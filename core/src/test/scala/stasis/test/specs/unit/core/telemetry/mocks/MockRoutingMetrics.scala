package stasis.test.specs.unit.core.telemetry.mocks

import stasis.core.routing.Metrics

import java.util.concurrent.atomic.AtomicInteger

object MockRoutingMetrics {
  class Router extends Metrics.Router {
    private val pushRecorded: AtomicInteger = new AtomicInteger(0)
    private val pushFailuresRecorded: AtomicInteger = new AtomicInteger(0)
    private val pullRecorded: AtomicInteger = new AtomicInteger(0)
    private val pullFailuresRecorded: AtomicInteger = new AtomicInteger(0)
    private val discardRecorded: AtomicInteger = new AtomicInteger(0)
    private val discardFailuresRecorded: AtomicInteger = new AtomicInteger(0)
    private val reserveRecorded: AtomicInteger = new AtomicInteger(0)
    private val reserveFailuresRecorded: AtomicInteger = new AtomicInteger(0)
    private val stageRecorded: AtomicInteger = new AtomicInteger(0)

    def push: Int = pushRecorded.get()
    def pushFailures: Int = pushFailuresRecorded.get()
    def pull: Int = pullRecorded.get()
    def pullFailures: Int = pullFailuresRecorded.get()
    def discard: Int = discardRecorded.get()
    def discardFailures: Int = discardFailuresRecorded.get()
    def reserve: Int = reserveRecorded.get()
    def reserveFailures: Int = reserveFailuresRecorded.get()
    def stage: Int = stageRecorded.get()

    override def recordPush(router: String, bytes: Long): Unit = {
      val _ = pushRecorded.incrementAndGet()
    }

    override def recordPushFailure(router: String, reason: Metrics.Router.PushFailure): Unit = {
      val _ = pushFailuresRecorded.incrementAndGet()
    }

    override def recordPull(router: String, bytes: Long): Unit = {
      val _ = pullRecorded.incrementAndGet()
    }

    override def recordPullFailure(router: String, reason: Metrics.Router.PullFailure): Unit = {
      val _ = pullFailuresRecorded.incrementAndGet()
    }

    override def recordDiscard(router: String, bytes: Long): Unit = {
      val _ = discardRecorded.incrementAndGet()
    }

    override def recordDiscardFailure(router: String, reason: Metrics.Router.DiscardFailure): Unit = {
      val _ = discardFailuresRecorded.incrementAndGet()
    }

    override def recordReserve(router: String, bytes: Long): Unit = {
      val _ = reserveRecorded.incrementAndGet()
    }

    override def recordReserveFailure(router: String, reason: Metrics.Router.ReserveFailure): Unit = {
      val _ = reserveFailuresRecorded.incrementAndGet()
    }

    override def recordStage(router: String, bytes: Long): Unit = {
      val _ = stageRecorded.incrementAndGet()
    }
  }

  object Router {
    def apply(): Router = new Router()
  }
}
