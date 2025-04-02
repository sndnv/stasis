package stasis.core.discovery.providers.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Random
import scala.util.Success

import org.apache.pekko.actor.typed.ActorSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.discovery.ServiceApiClient
import stasis.core.discovery.ServiceDiscoveryClient
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.discovery.exceptions.DiscoveryFailure

trait ServiceDiscoveryProvider {
  def latest[T <: ServiceApiClient](implicit tag: ClassTag[T]): T
}

object ServiceDiscoveryProvider {
  class Disabled(
    initialClients: Seq[ServiceApiClient]
  ) extends ServiceDiscoveryProvider {
    override def latest[T <: ServiceApiClient](implicit tag: ClassTag[T]): T =
      extractClient[T](from = initialClients)
  }

  class Default(
    interval: FiniteDuration,
    initialClients: Map[String, ServiceApiClient],
    clientFactory: ServiceApiClient.Factory
  )(implicit system: ActorSystem[Nothing], log: Logger)
      extends ServiceDiscoveryProvider {
    import system.executionContext

    private val clients: ConcurrentHashMap[String, ServiceApiClient] = new ConcurrentHashMap(initialClients.asJava)

    scheduleNext(after = fullInterval())

    override def latest[T <: ServiceApiClient](implicit tag: ClassTag[T]): T = {
      val rnd = Random.javaRandomToRandom(ThreadLocalRandom.current())

      val collected = clients.asScala.values.collect { case client: T => client }

      extractClient[T](from = rnd.shuffle(collected))
    }

    private def scheduleNext(after: FiniteDuration): Unit = {
      log.debug("Scheduling next service discovery in [{}] second(s)", after.toSeconds)

      org.apache.pekko.pattern
        .after(duration = after)(
          discoverServices(
            client = extractClient[ServiceDiscoveryClient](from = clients.values().asScala),
            isInitialRequest = false
          )
        )
        .onComplete {
          case Success(ServiceDiscoveryResult.KeepExisting) =>
            log.debug("Service discovery did not provide new endpoints")

            scheduleNext(fullInterval())

          case Success(ServiceDiscoveryResult.SwitchTo(endpoints, recreateExisting)) =>
            log.debug(
              "Service discovery provided endpoints [api={},core={},discovery={}] with [recreateExisting={}]",
              endpoints.api.id,
              endpoints.core.id,
              endpoints.discovery.id,
              recreateExisting
            )

            if (recreateExisting) {
              clients.clear()

              val core = clientFactory.create(endpoints.core)
              val _ = clients.put(endpoints.core.id, core)
              val _ = clients.put(endpoints.api.id, clientFactory.create(endpoints.api, core))
              val _ = clients.put(endpoints.discovery.id, clientFactory.create(endpoints.discovery))
            } else {
              val core = clients.computeIfAbsent(endpoints.core.id, _ => clientFactory.create(endpoints.core))
              val _ = clients.computeIfAbsent(endpoints.api.id, _ => clientFactory.create(endpoints.api, core))
              val _ = clients.computeIfAbsent(endpoints.discovery.id, _ => clientFactory.create(endpoints.discovery))

              clients.keySet.asScala
                .filter(k => k != endpoints.core.id && k != endpoints.api.id && k != endpoints.discovery.id)
                .foreach(clients.remove)
            }

            scheduleNext(fullInterval())

          case Failure(e) =>
            log.error("Service discovery failed with [{} - {}]", e.getClass.getSimpleName, e.getMessage)

            scheduleNext(reducedInterval())
        }
    }

    private def fullInterval(): FiniteDuration =
      fuzzyInterval(interval = interval)

    private def reducedInterval(): FiniteDuration =
      fuzzyInterval(interval = interval / FailureIntervalReduction)

    private def fuzzyInterval(interval: FiniteDuration): FiniteDuration = {
      val intervalMs = interval.toMillis
      val low = (intervalMs - (intervalMs * 0.02)).toLong
      val high = (intervalMs + (intervalMs * 0.03)).toLong

      ThreadLocalRandom.current().nextLong(low, high).millis
    }
  }

  def apply(
    interval: FiniteDuration,
    initialClients: Seq[ServiceApiClient],
    clientFactory: ServiceApiClient.Factory
  )(implicit system: ActorSystem[Nothing]): Future[ServiceDiscoveryProvider] = {
    import system.executionContext

    implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    discoverServices(client = extractClient[ServiceDiscoveryClient](from = initialClients), isInitialRequest = true).map {
      case ServiceDiscoveryResult.KeepExisting =>
        log.debug("Initial service discovery did not provide any endpoints; discovery disabled")
        new Disabled(initialClients = initialClients)

      case ServiceDiscoveryResult.SwitchTo(endpoints, _) =>
        log.debug(
          "Initial service discovery provided endpoints [api={},core={},discovery={}]",
          endpoints.api.id,
          endpoints.core.id,
          endpoints.discovery.id
        )

        val core = clientFactory.create(endpoints.core)

        new Default(
          interval = interval,
          initialClients = Map(
            endpoints.core.id -> core,
            endpoints.api.id -> clientFactory.create(endpoints.api, core),
            endpoints.discovery.id -> clientFactory.create(endpoints.discovery)
          ),
          clientFactory = clientFactory
        )
    }
  }

  private def discoverServices(client: ServiceDiscoveryClient, isInitialRequest: Boolean): Future[ServiceDiscoveryResult] =
    client.latest(isInitialRequest)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def extractClient[T <: ServiceApiClient](from: Iterable[ServiceApiClient])(implicit tag: ClassTag[T]): T =
    from.collectFirst { case client: T => client } match {
      case Some(client) => client
      case None         => throw new DiscoveryFailure(s"Service client [${tag.toString()}] was not found")
    }

  final val FailureIntervalReduction: Long = 10L
}
