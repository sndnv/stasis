package stasis.layers.service

import java.io.File

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.slf4j.LoggerFactory

import stasis.layers.service.bootstrap.BootstrapEntityProvider
import stasis.layers.service.bootstrap.BootstrapExecutor

trait BootstrapProvider {
  def run(): Future[BootstrapProvider.BootstrapMode]
}

object BootstrapProvider {
  sealed trait BootstrapMode {
    lazy val name: String = getClass.getSimpleName.replaceAll("[^a-zA-Z0-9]", "")
  }

  object BootstrapMode {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def apply(mode: String): BootstrapMode =
      mode.toLowerCase match {
        case "off"            => Off
        case "init"           => Init
        case "init-and-start" => InitAndStart
        case "drop"           => Drop
        case _                => throw new IllegalArgumentException(s"Unsupported bootstrap mode provided: [$mode]")
      }

    final case object Off extends BootstrapMode
    final case object Init extends BootstrapMode
    final case object InitAndStart extends BootstrapMode
    final case object Drop extends BootstrapMode
  }

  class Default(
    bootstrapConfig: typesafe.Config,
    persistence: PersistenceProvider,
    bootstrap: BootstrapExecutor
  )(implicit ec: ExecutionContext)
      extends BootstrapProvider {
    private val log = LoggerFactory.getLogger(this.getClass.getName)

    override def run(): Future[BootstrapMode] = Future {
      val mode = BootstrapMode(bootstrapConfig.getString("mode"))

      val result = mode match {
        case BootstrapMode.Off =>
          log.debug("Bootstrap mode disabled; running migrations only...")
          persistence
            .migrate()
            .map { result =>
              log.debug("Executed [{}] out of [{}] migrations", result.executed, result.found)
            }

        case BootstrapMode.Init | BootstrapMode.InitAndStart =>
          val configFile = bootstrapConfig.getString("config").trim
          require(configFile.nonEmpty, "Bootstrap enabled but no config file was provided")
          val config = loadBootstrapFile(configFile)

          log.debug("Running bootstrap in [{}] mode...", mode.name)

          for {
            _ <- persistence.init()
            migrationResult <- persistence.migrate()
            bootstrapResult <- bootstrap.execute(config)
          } yield {
            log.debug("Executed [{}] out of [{}] migrations", migrationResult.executed, migrationResult.found)
            log.debug("Created [{}] out of [{}] bootstrap entities", bootstrapResult.created, bootstrapResult.found)
            Done
          }

        case BootstrapMode.Drop =>
          log.warn("Running bootstrap in [{}] mode...", mode.name)

          persistence.drop()
      }

      result.map(_ => mode)
    }.flatten

    private def loadBootstrapFile(configFile: String): typesafe.Config =
      typesafe.ConfigFactory
        .parseFile(
          Option(getClass.getClassLoader.getResource(configFile))
            .map(resource => new File(resource.getFile))
            .getOrElse(new File(configFile))
        )
        .resolve()
        .getConfig("bootstrap")
  }

  def apply(
    bootstrapConfig: typesafe.Config,
    persistence: PersistenceProvider,
    entityProviders: Seq[BootstrapEntityProvider[_ <: Product]]
  )(implicit ec: ExecutionContext): BootstrapProvider =
    new Default(
      bootstrapConfig = bootstrapConfig,
      persistence = persistence,
      bootstrap = BootstrapExecutor(entityProviders)
    )
}
