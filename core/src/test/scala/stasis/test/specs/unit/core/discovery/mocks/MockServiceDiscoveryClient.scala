package stasis.test.specs.unit.core.discovery.mocks

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.ServiceDiscoveryResult

class MockServiceDiscoveryClient(
  initialDiscoveryResult: ServiceDiscoveryResult,
  nextDiscoveryResult: ServiceDiscoveryResult
) extends ServiceDiscoveryClient {
  private val counterRef: AtomicInteger = new AtomicInteger(0)

  override val attributes: ServiceDiscoveryClient.Attributes =
    MockServiceDiscoveryClient.TestAttributes(a = "b")

  override def latest(isInitialRequest: Boolean): Future[ServiceDiscoveryResult] = {
    val _ = counterRef.incrementAndGet()

    Future.successful(
      if (isInitialRequest) initialDiscoveryResult
      else nextDiscoveryResult
    )
  }

  def results: Int = counterRef.get()
}

object MockServiceDiscoveryClient {
  def apply(): MockServiceDiscoveryClient =
    new MockServiceDiscoveryClient(
      initialDiscoveryResult = ServiceDiscoveryResult.KeepExisting,
      nextDiscoveryResult = ServiceDiscoveryResult.KeepExisting
    )

  def apply(
    initialDiscoveryResult: ServiceDiscoveryResult,
    nextDiscoveryResult: ServiceDiscoveryResult
  ): MockServiceDiscoveryClient = new MockServiceDiscoveryClient(
    initialDiscoveryResult = initialDiscoveryResult,
    nextDiscoveryResult = nextDiscoveryResult
  )

  final case class TestAttributes(a: String) extends ServiceDiscoveryClient.Attributes
}
