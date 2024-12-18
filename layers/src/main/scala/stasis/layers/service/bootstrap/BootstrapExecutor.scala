package stasis.layers.service.bootstrap

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.Config
import org.slf4j.Logger
import org.slf4j.LoggerFactory

trait BootstrapExecutor {
  def execute(config: Config): Future[BootstrapResult]
}

object BootstrapExecutor {
  class Default(
    entityProviders: Seq[BootstrapEntityProvider[_ <: Product]]
  )(implicit ec: ExecutionContext)
      extends BootstrapExecutor {
    private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    def execute(config: Config): Future[BootstrapResult] = entityProviders
      .foldLeft(Future.successful(BootstrapResult.empty)) { case (collected, provider) =>
        for {
          latest <- collected
          current <- run[provider.EntityType](config = config, withProvider = provider).recover { case NonFatal(e) =>
            log.error(
              "Bootstrap execution for provider [{}] failed with [{} - {}]",
              provider.name,
              e.getClass.getSimpleName,
              e.getMessage
            )

            BootstrapResult.empty
          }
        } yield {
          latest + current
        }
      }
  }

  def apply(
    entityProviders: Seq[BootstrapEntityProvider[_ <: Product]]
  )(implicit ec: ExecutionContext): BootstrapExecutor =
    new Default(entityProviders)

  def run[T <: Product](
    config: Config,
    withProvider: BootstrapEntityProvider[T]
  )(implicit log: Logger, ec: ExecutionContext): Future[BootstrapResult] =
    for {
      configuredEntities <- Future.fromTry(Try(config.getConfigList(withProvider.name).asScala.toSeq.map(withProvider.load)))
      defaultEntities = withProvider.default
      entities = defaultEntities ++ configuredEntities
      _ = log.debug(
        "Bootstrap provider for [{}] found [{}] entities (configured={}, default={}): [{}\n]",
        withProvider.name,
        entities.length,
        configuredEntities.length,
        defaultEntities.length,
        renderEntities(entities, withProvider)
      )
      _ <- withProvider.validate(entities)
      created <- entities.foldLeft(Future.successful(0)) { case (collected, entity) =>
        collected.flatMap { latest =>
          withProvider
            .create(entity)
            .map { _ =>
              log.info(
                "Bootstrap provider for [{}] successfully created entity [{}]",
                withProvider.name,
                withProvider.extractId(entity)
              )

              latest + 1
            }
            .recover { case NonFatal(e) =>
              log.error(
                "Bootstrap provider for [{}] failed to create entity [{}]: [{} - {}]",
                withProvider.name,
                withProvider.extractId(entity),
                e.getClass.getSimpleName,
                e.getMessage
              )

              latest
            }
        }
      }
    } yield {
      val result = BootstrapResult(found = entities.length, created = created)
      log.info(
        "Bootstrap provider for [{}] created [{}] out of [{}] bootstrap entities",
        withProvider.name,
        result.created,
        result.found
      )
      result
    }

  private def renderEntities[T <: Product](entities: Seq[T], withProvider: BootstrapEntityProvider[T]): String =
    entities.map(withProvider.render(_, withPrefix = "\t")).mkString(",")
}
