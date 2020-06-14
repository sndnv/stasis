package stasis.client.service

import java.io.Console
import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.components.exceptions.ServiceStartupFailure

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

trait Service {
  import Service._

  private val clientState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = "stasis-client-service"
  )

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  protected def applicationName: String = Service.ApplicationName

  protected def applicationDirectory: ApplicationDirectory =
    ApplicationDirectory.Default(applicationName = applicationName)

  protected def console: Option[Console] = Option(System.console())

  private val startupPromise: Promise[Done] = Promise()

  private val startup = for {
    base <- components.Base(applicationDirectory = applicationDirectory, terminate = stop)
    init <- components.Init(base, startup = startupPromise.future, console = console)
    secrets <- components.Secrets(base, init)
    clients <- components.ApiClients(base, secrets)
    ops <- components.Ops(base, clients, secrets)
    endpoint <- components.ApiEndpoint(base, clients, ops)
    _ <- endpoint.api.start()
  } yield {
    Done
  }

  startup.onComplete {
    case Success(_) =>
      log.info("Client startup completed")
      clientState.set(State.Started)
      val _ = startupPromise.success(Done)

    case Failure(e) =>
      val failure = e match {
        case e: ServiceStartupFailure               => e
        case e: com.typesafe.config.ConfigException => ServiceStartupFailure.config(e)
        case e: Throwable                           => ServiceStartupFailure.unknown(e)
      }

      log.error("Client startup failed: [{}]", failure.message)
      clientState.set(State.StartupFailed(failure))
      val _ = startupPromise.failure(failure)

      delayed(stop(), by = FailureTerminationDelay)
  }

  def stop(): Unit = {
    log.info("Client stopping...")
    system.terminate()
  }

  def state: State = clientState.get()

  private val _ = sys.addShutdownHook(stop())
}

object Service {
  final val ApplicationName: String = "stasis-client"
  final val FailureTerminationDelay: FiniteDuration = 250.millis

  sealed trait State
  object State {
    case object Starting extends State
    final object Started extends State
    final case class StartupFailed(throwable: Throwable) extends State
  }

  private def delayed(
    op: => Unit,
    by: FiniteDuration
  )(implicit system: ActorSystem[SpawnProtocol.Command]): Unit = {
    val _ = akka.pattern.after(
      duration = by,
      using = system.classicSystem.scheduler
    ) { Future.successful(op) }(system.executionContext)
  }
}
