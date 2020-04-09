package stasis.client.ops.recovery.stages

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
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
    for {
      crate <- Future.fromTry(entity.existingMetadata.collectCrate)
      source <- pull(crate, entity.originalPath)
      _ <- destage(source, entity)
    } yield {
      entity
    }

  private def processMetadataChanged(entity: TargetEntity): Future[TargetEntity] =
    Future.successful(entity)

  private def pull(crate: Crate.Id, entity: Path): Future[Source[ByteString, NotUsed]] =
    providers.clients.core
      .pull(crate)
      .flatMap {
        case Some(source) => Future.successful(source)
        case None         => Future.failed(PullFailure(s"Failed to pull crate [$crate] for entity [$entity]"))
      }

  private def destage(
    source: Source[ByteString, NotUsed],
    entity: TargetEntity
  ): Future[Done] =
    providers.staging
      .temporary()
      .flatMap { staged =>
        source
          .via(providers.decryptor.decrypt(deviceSecret.toFileSecret(entity.originalPath)))
          .via(providers.decompressor.decompress)
          .runWith(FileIO.toPath(staged))
          .flatMap(result => Future.fromTry(result.status))
          .flatMap(_ => providers.staging.destage(from = staged, to = entity.destinationPath))
      }

  private val targetDirectoryPermissions = "rwx------"

  private val targetDirectoryAttributes =
    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(targetDirectoryPermissions))
}
