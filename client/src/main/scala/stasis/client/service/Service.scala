package stasis.client.service

import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.Behaviors
import akka.event.{Logging, LoggingAdapter}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait Service {
  import Service._

  private val clientState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    name = "stasis-client-service"
  )

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val untyped: akka.actor.ActorSystem = system.toUntyped
  private implicit val log: LoggingAdapter = Logging(untyped, this.getClass.getName)

  protected def applicationName: String = Service.ApplicationName

  protected def applicationDirectory: ApplicationDirectory =
    ApplicationDirectory.Default(applicationName = applicationName)

  protected def credentialsReader: CredentialsReader =
    CredentialsReader.UsernameAndPassword()

  private val init = for {
    base <- components.Base(applicationDirectory = applicationDirectory, terminate = stop)
    secrets <- components.Secrets(base, credentialsReader = credentialsReader)
    clients <- components.ApiClients(base, secrets)
    ops <- components.Ops(base, clients, secrets)
    endpoint <- components.ApiEndpoint(base, clients, ops)
  } yield {
    endpoint.api.start()
    Done
  }

  init.onComplete {
    case Success(_) =>
      log.info("Client startup completed")
      clientState.set(State.Started)

    case Failure(e) =>
      log.error(e, "Client startup failed: [{}]", e.getMessage)
      clientState.set(State.StartupFailed(e))
      stop()
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

  sealed trait State
  object State {
    case object Starting extends State
    final object Started extends State
    final case class StartupFailed(throwable: Throwable) extends State
  }
}
