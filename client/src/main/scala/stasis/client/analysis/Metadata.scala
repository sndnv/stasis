package stasis.client.analysis

import java.nio.file.attribute.{FileTime, PosixFileAttributeView, PosixFileAttributes, PosixFilePermissions}
import java.nio.file.{Files, LinkOption, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.Done
import akka.stream.Materializer
import stasis.client.model.{EntityMetadata, SourceEntity, TargetEntity}
import stasis.core.packaging.Crate

import scala.concurrent.{ExecutionContext, Future}

object Metadata {
  def collectSource(
    checksum: Checksum,
    entity: Path,
    existingMetadata: Option[EntityMetadata]
  )(implicit mat: Materializer): Future[SourceEntity] = {
    implicit val ec: ExecutionContext = mat.executionContext

    for {
      baseMetadata <- extractBaseEntityMetadata(entity)
      entityMetadata <- collectEntityMetadata(
        currentMetadata = baseMetadata,
        collectCrate = checksum => collectCrateForSourceFile(existingMetadata, checksum),
        checksum = checksum
      )
    } yield {
      SourceEntity(
        path = entity,
        existingMetadata = existingMetadata,
        currentMetadata = entityMetadata
      )
    }
  }

  def collectTarget(
    checksum: Checksum,
    entity: Path,
    destination: TargetEntity.Destination,
    existingMetadata: EntityMetadata
  )(implicit mat: Materializer): Future[TargetEntity] = {
    implicit val ec: ExecutionContext = mat.executionContext

    val targetEntity = TargetEntity(
      path = entity,
      destination = destination,
      existingMetadata = existingMetadata,
      currentMetadata = None
    )

    val destinationPath = targetEntity.destinationPath

    if (Files.exists(destinationPath)) {
      for {
        baseMetadata <- extractBaseEntityMetadata(destinationPath)
        entityMetadata <- collectEntityMetadata(
          currentMetadata = baseMetadata,
          collectCrate = _ => collectCrateForTargetFile(existingMetadata),
          checksum = checksum
        )
      } yield {
        targetEntity.copy(currentMetadata = Some(entityMetadata))
      }
    } else {
      Future.successful(targetEntity)
    }
  }

  def collectEntityMetadata(
    currentMetadata: BaseEntityMetadata,
    checksum: Checksum,
    collectCrate: BigInt => Future[Crate.Id]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[EntityMetadata] =
    if (currentMetadata.isDirectory) {
      Future.successful(
        EntityMetadata.Directory(
          path = currentMetadata.path,
          link = currentMetadata.link,
          isHidden = currentMetadata.isHidden,
          created = currentMetadata.created,
          updated = currentMetadata.updated,
          owner = currentMetadata.owner,
          group = currentMetadata.group,
          permissions = currentMetadata.permissions
        )
      )
    } else {
      for {
        currentChecksum <- checksum.calculate(currentMetadata.path)
        crate <- collectCrate(currentChecksum)
      } yield {
        EntityMetadata.File(
          path = currentMetadata.path,
          size = currentMetadata.attributes.size,
          link = currentMetadata.link,
          isHidden = currentMetadata.isHidden,
          created = currentMetadata.created,
          updated = currentMetadata.updated,
          owner = currentMetadata.owner,
          group = currentMetadata.group,
          permissions = currentMetadata.permissions,
          checksum = currentChecksum,
          crate = crate
        )
      }
    }

  def collectCrateForSourceFile(existingMetadata: Option[EntityMetadata], currentChecksum: BigInt): Future[Crate.Id] =
    existingMetadata match {
      case Some(file: EntityMetadata.File) if file.checksum == currentChecksum =>
        Future.successful(file.crate)

      case Some(directory: EntityMetadata.Directory) =>
        Future.failed(
          new IllegalArgumentException(s"Expected metadata for file but directory metadata [${directory.path}] provided")
        )

      case _ =>
        Future.successful(Crate.generateId())
    }

  def collectCrateForTargetFile(existingMetadata: EntityMetadata): Future[Crate.Id] =
    existingMetadata match {
      case file: EntityMetadata.File =>
        Future.successful(file.crate)

      case directory: EntityMetadata.Directory =>
        Future.failed(
          new IllegalArgumentException(s"Expected metadata for file but directory metadata [${directory.path}] provided")
        )
    }

  def extractBaseEntityMetadata(
    entity: Path
  )(implicit ec: ExecutionContext): Future[BaseEntityMetadata] =
    Future {
      val attributes = Files.readAttributes(entity, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

      val isDirectory = attributes.isDirectory
      val link = if (Files.isSymbolicLink(entity)) Some(Files.readSymbolicLink(entity)) else None
      val isHidden = Files.isHidden(entity)
      val created = attributes.creationTime.toInstant.truncatedTo(ChronoUnit.SECONDS)
      val updated = attributes.lastModifiedTime.toInstant.truncatedTo(ChronoUnit.SECONDS)
      val owner = attributes.owner.getName
      val group = attributes.group.getName
      val permissions = PosixFilePermissions.toString(attributes.permissions())

      BaseEntityMetadata(
        path = entity,
        isDirectory = isDirectory,
        link = link,
        isHidden = isHidden,
        created = created,
        updated = updated,
        owner = owner,
        group = group,
        permissions = permissions,
        attributes = attributes
      )
    }

  def applyEntityMetadataTo(metadata: EntityMetadata, entity: Path)(implicit ec: ExecutionContext): Future[Done] =
    Future {
      val attributes = Files.getFileAttributeView(entity, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)

      attributes.setPermissions(PosixFilePermissions.fromString(metadata.permissions))

      val lookupService = entity.getFileSystem.getUserPrincipalLookupService

      val owner = lookupService.lookupPrincipalByName(metadata.owner)
      val group = lookupService.lookupPrincipalByGroupName(metadata.group)

      attributes.setOwner(owner)
      attributes.setGroup(group)

      attributes.setTimes(
        /* lastModifiedTime */ FileTime.from(metadata.updated),
        /* lastAccessTime */ FileTime.from(Instant.now()),
        /* createTime */ FileTime.from(metadata.created)
      )

      Done
    }

  final case class BaseEntityMetadata(
    path: Path,
    isDirectory: Boolean,
    link: Option[Path],
    isHidden: Boolean,
    created: Instant,
    updated: Instant,
    owner: String,
    group: String,
    permissions: String,
    attributes: PosixFileAttributes
  )
}
