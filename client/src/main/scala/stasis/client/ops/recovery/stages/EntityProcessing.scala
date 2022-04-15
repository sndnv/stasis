package stasis.client.ops.recovery.stages

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.TargetEntity.Destination
import stasis.client.model.{EntityMetadata, TargetEntity}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.core.packaging.Crate
import stasis.core.routing.exceptions.PullFailure
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}

trait EntityProcessing {
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def entityProcessing(implicit operation: Operation.Id): Flow[TargetEntity, TargetEntity, NotUsed] =
    Flow[TargetEntity]
      .collect {
        case entity @ TargetEntity(_, Destination.Default, _, _)                                  => createEntityDirectory(entity)
        case entity @ TargetEntity(_, Destination.Directory(_, true), _, _)                       => createEntityDirectory(entity)
        case entity @ TargetEntity(_, Destination.Directory(_, false), _: EntityMetadata.File, _) => entity
      }
      .map(createEntityDirectory)
      .mapAsync(parallelism.value) {
        case entity if entity.hasContentChanged => processContentChanged(entity)
        case entity                             => processMetadataChanged(entity)
      }
      .wireTap(targetEntity => providers.track.entityProcessed(entity = targetEntity.destinationPath))

  private def createEntityDirectory(entity: TargetEntity): TargetEntity = {
    val entityDirectory = entity.existingMetadata match {
      case _: EntityMetadata.File      => entity.destinationPath.getParent
      case _: EntityMetadata.Directory => entity.destinationPath
    }

    val _ = Files.createDirectories(entityDirectory, targetDirectoryAttributes)

    entity
  }

  private def processContentChanged(entity: TargetEntity): Future[TargetEntity] =
    EntityProcessing.expectFileMetadata(entity).flatMap { file =>
      pull(file.crates, entity.originalPath)
        .flatMap { crates =>
          implicit val prv: Providers = providers

          crates
            .decrypt(withPartSecret = deviceSecret.toFileSecret)
            .merge()
            .decompress()
            .destage(to = entity.destinationPath)
        }
        .map(_ => entity)
    }

  private def processMetadataChanged(entity: TargetEntity): Future[TargetEntity] =
    Future.successful(entity)

  private def pull(crates: Map[Path, Crate.Id], entity: Path): Future[Iterable[(Path, Source[ByteString, NotUsed])]] =
    Future
      .sequence(
        crates.map { case (partPath, crate) =>
          providers.clients.core
            .pull(crate)
            .flatMap {
              case Some(source) =>
                Future.successful((partPath, source))

              case None =>
                Future.failed(
                  PullFailure(
                    s"Failed to pull crate [${crate.toString}] for entity [${entity.toString}]"
                  )
                )
            }
        }
      )

  private val targetDirectoryPermissions = "rwx------"

  private val targetDirectoryAttributes =
    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(targetDirectoryPermissions))

  private type CratesSources = Iterable[(Path, Source[ByteString, NotUsed])]
  private type EntitySource = Source[ByteString, NotUsed]

  private implicit class DecryptedCrates(crates: CratesSources) extends internal.DecryptedCrates(crates)
  private implicit class MergedCrates(crates: CratesSources) extends internal.MergedCrates(crates)
  private implicit class DecompressedByteStringSource(source: EntitySource) extends internal.DecompressedByteStringSource(source)
  private implicit class DestagedByteStringSource(source: EntitySource) extends internal.DestagedByteStringSource(source)
}

object EntityProcessing {
  def expectFileMetadata(entity: TargetEntity): Future[EntityMetadata.File] =
    entity.existingMetadata match {
      case file: EntityMetadata.File =>
        Future.successful(file)

      case directory: EntityMetadata.Directory =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path.toString}] provided"
          )
        )
    }
}
