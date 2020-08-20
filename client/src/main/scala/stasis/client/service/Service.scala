package stasis.client.service

import java.io.Console
import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.service.components.exceptions.ServiceStartupFailure

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

trait Service { _: Service.Arguments =>
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

  protected def applicationArguments: Future[ApplicationArguments] =
    arguments(applicationName)

  protected def console: Option[Console] = Option(System.console())

  private val startupPromise: Promise[Done] = Promise()

  private val startup: Future[ApplicationArguments.Mode] = applicationArguments.flatMap {
    case ApplicationArguments(mode: ApplicationArguments.Mode.Bootstrap) =>
      for {
        base <- components.bootstrap.Base(modeArguments = mode, applicationDirectory = applicationDirectory)
        init <- components.bootstrap.Init(base, console)
        bootstrap <- components.bootstrap.Bootstrap(base, init)
        params <- components.bootstrap.Parameters(base, bootstrap)
        _ <- params.apply()
        secrets <- components.bootstrap.Secrets(base, init)
        _ <- secrets.create()
      } yield {
        mode
      }

    case ApplicationArguments(ApplicationArguments.Mode.Service) =>
      for {
        base <- components.Base(applicationDirectory = applicationDirectory, terminate = stop)
        init <- components.Init(base, startup = startupPromise.future, console = console)
        secrets <- components.Secrets(base, init)
        clients <- components.ApiClients(base, secrets)
        ops <- components.Ops(base, clients, secrets)
        endpoint <- components.ApiEndpoint(base, clients, ops)
        _ <- endpoint.api.start()
      } yield {
        ApplicationArguments.Mode.Service
      }
  }

  startup.onComplete {
    case Success(mode) if mode == ApplicationArguments.Mode.Service =>
      log.info("Client startup completed")
      clientState.set(State.Started)
      val _ = startupPromise.success(Done)

    case Success(_) =>
      log.info("Client bootstrap completed")
      clientState.set(State.Completed)
      val _ = startupPromise.success(Done)
      stop()

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

  locally {
    val _ = sys.addShutdownHook(stop())
  }
}

object Service {
  final val ApplicationName: String = "stasis-client"
  final val FailureTerminationDelay: FiniteDuration = 250.millis

  sealed trait State
  object State {
    case object Starting extends State
    final object Started extends State
    final object Completed extends State
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

  trait Arguments extends App {
    def raw: Array[String] = args

    def provided(implicit system: ActorSystem[SpawnProtocol.Command]): Future[Array[String]] = {
      implicit val scheduler: Scheduler = system.classicSystem.scheduler
      implicit val ec: ExecutionContext = system.executionContext

      akka.pattern
        .retry(
          attempt = () => {
            Option(raw) match {
              case Some(retrieved) => Future.successful(retrieved)
              case None            => Future.failed(new IllegalArgumentException("No arguments provided"))
            }
          },
          attempts = Arguments.RetryAttempts,
          delay = Arguments.RetryDelay
        )
    }

    def arguments(applicationName: String)(implicit system: ActorSystem[SpawnProtocol.Command]): Future[ApplicationArguments] = {
      import system.executionContext
      provided.flatMap(ApplicationArguments(applicationName, _))
    }
  }

  object Arguments {
    final val RetryAttempts: Int = 10
    final val RetryDelay: FiniteDuration = 25.millis
  }
}
