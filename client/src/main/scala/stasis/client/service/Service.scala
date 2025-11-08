package stasis.client.service

import java.io.Console
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.Done
import org.apache.pekko.actor.Scheduler
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.service.components.exceptions.ServiceStartupFailure

trait Service { _: Service.Arguments =>
  import Service._

  private val clientState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "stasis-client-service"
  )

  private implicit val ec: ExecutionContext = system.executionContext
  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  protected def applicationName: String = Service.ApplicationName

  protected def applicationDirectory: ApplicationDirectory =
    ApplicationDirectory.Default(applicationName = applicationName)

  protected def applicationArguments: Future[ApplicationArguments] =
    arguments(applicationName)

  protected def applicationTray: ApplicationTray =
    ApplicationTray(callbacks = createCallbacks(forService = this))

  protected def console: Option[Console] = Option(System.console())

  private val startupPromise: Promise[Done] = Promise()

  private val startup: Future[ApplicationArguments.Mode] = applicationArguments.flatMap {
    case ApplicationArguments(mode: ApplicationArguments.Mode.Bootstrap) =>
      implicit val log: Logger = LoggerFactory.getLogger("stasis.client.bootstrap")

      for {
        _ <- ApplicationRuntimeRequirements.validate()
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
      implicit val log: Logger = LoggerFactory.getLogger("stasis.client.service")

      for {
        _ <- ApplicationRuntimeRequirements.validate()
        base <- components.Base(applicationDirectory = applicationDirectory, applicationTray = applicationTray, terminate = stop)
        tracking <- components.Tracking(base)
        init <- components.Init(base, startup = startupPromise.future, console = console)
        secrets <- components.Secrets(base, init)
        clients <- components.ApiClients(base, secrets)
        ops <- components.Ops(base, tracking, clients, secrets)
        endpoint <- components.ApiEndpoint(base, tracking, clients, ops, secrets)
        _ <- endpoint.api.start()
      } yield {
        base.tray.init()
        ApplicationArguments.Mode.Service
      }

    case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
      implicit val log: Logger = LoggerFactory.getLogger("stasis.client.maintenance")

      for {
        _ <- ApplicationRuntimeRequirements.validate()
        base <- components.maintenance.Base(modeArguments = mode, applicationDirectory = applicationDirectory)
        init <- components.maintenance.Init(base, console = console)
        mode <- init.retrieveCredentials()
        certificates <- components.maintenance.Certificates(base)
        _ <- certificates.apply()
        credentials <- components.maintenance.Credentials(base, mode)
        _ <- credentials.apply()
        secrets <- components.maintenance.Secrets(base, mode)
        _ <- secrets.apply()
      } yield {
        mode
      }
  }

  startup.onComplete {
    case Success(mode) if mode == ApplicationArguments.Mode.Service =>
      log.info("Client startup completed")
      clientState.set(State.Started)
      val _ = startupPromise.success(Done)

    case Success(mode) =>
      log.info("Client {} completed", mode.getClass.getSimpleName.toLowerCase)
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
    applicationTray.shutdown()
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
  )(implicit system: ActorSystem[Nothing]): Unit = {
    val _ = org.apache.pekko.pattern.after(
      duration = by,
      using = system.classicSystem.scheduler
    ) { Future.successful(op) }(system.executionContext)
  }

  trait Arguments extends App {
    def raw: Array[String] = args

    def provided(implicit system: ActorSystem[Nothing]): Future[Array[String]] = {
      implicit val scheduler: Scheduler = system.classicSystem.scheduler
      implicit val ec: ExecutionContext = system.executionContext

      org.apache.pekko.pattern
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

    def arguments(applicationName: String)(implicit system: ActorSystem[Nothing]): Future[ApplicationArguments] = {
      import system.executionContext
      provided.flatMap(ApplicationArguments(applicationName, _))
    }
  }

  object Arguments {
    final val RetryAttempts: Int = 10
    final val RetryDelay: FiniteDuration = 25.millis
  }

  def createCallbacks(forService: Service): ApplicationTray.Callbacks =
    createCallbacks(forService = forService, withRuntime = Runtime.getRuntime)

  def createCallbacks(forService: Service, withRuntime: Runtime): ApplicationTray.Callbacks =
    ApplicationTray.Callbacks(
      terminateService = forService.stop,
      startUiService = () => { val _ = withRuntime.exec(startUiCommand().toArray) }
    )

  def startUiCommand(): Seq[String] = startUiCommand(
    osName = System.getProperty("os.name"),
    userHome = System.getProperty("user.home")
  )

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def startUiCommand(osName: String, userHome: String): Seq[String] =
    osName.toLowerCase.split(" ").headOption match {
      case Some("mac")   => Seq("open", s"$userHome/Applications/stasis.app")
      case Some("linux") => Seq("stasis-ui")
      case _             => throw new IllegalArgumentException(s"Operating system [$osName}] is not supported")
    }
}
