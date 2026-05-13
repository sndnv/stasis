package stasis.client.analysis

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import org.apache.pekko.Done
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.model.EntityMetadata

sealed trait PlatformMetadata {
  def platform: PlatformMetadata.Type
  def log: Logger

  def extractFrom(
    entity: Path
  )(implicit ec: ExecutionContext): Future[BaseEntityMetadata] =
    Future {
      val attributes = PlatformMetadata.attributesFor(platform, entity)

      val basic = PlatformMetadata.Attributes.Basic.extractFrom(entity, attributes)
      val ownership = PlatformMetadata.Attributes.Ownership.extractFrom(entity, attributes)
      val permissions = PlatformMetadata.Attributes.Permissions.extractFrom(entity, attributes)

      BaseEntityMetadata(
        path = entity,
        isDirectory = basic.isDirectory,
        link = basic.link,
        isHidden = basic.isHidden,
        created = basic.created,
        updated = basic.updated,
        owner = ownership.owner,
        group = ownership.group,
        permissions = permissions,
        attributes = attributes
      )
    }

  def applyTo(
    entity: Path,
    metadata: EntityMetadata
  )(implicit
    ec: ExecutionContext,
    defaults: PlatformMetadata.Defaults
  ): Future[Done] =
    Future {
      val attributes = PlatformMetadata.attributeViewFor(platform, entity)

      val (owner, group, permissions) = if (platform.matches(metadata.permissions)) {
        (metadata.owner, Option(metadata.group).filterNot(_.isBlank), metadata.permissions)
      } else {
        val defaultEntityPermissions = metadata match {
          case _: EntityMetadata.File      => defaults.permissionsFor(platform).files
          case _: EntityMetadata.Directory => defaults.permissionsFor(platform).directories
        }

        log.warn(
          "Cannot apply non-{} permissions [{}] to entity [{}]; applying default permissions [{}]",
          platform,
          metadata.permissions,
          entity,
          defaultEntityPermissions
        )

        (defaults.currentUser, None, defaultEntityPermissions)
      }

      PlatformMetadata.Attributes.Ownership.applyTo(
        entity = entity,
        attributes = attributes,
        owner = owner,
        group = group
      )

      PlatformMetadata.Attributes.Permissions.applyTo(
        entity = entity,
        attributes = attributes,
        permissions = permissions
      )

      PlatformMetadata.Attributes.Basic.applyTo(
        attributes = attributes,
        created = metadata.created,
        updated = metadata.updated
      )

      Done
    }

  def ownerOnlyFileAttributes: Seq[FileAttribute[_]] =
    PlatformMetadata.Attributes.Permissions.asAttributes(platform, "rw-------")

  def ownerOnlyDirectoryAttributes: Seq[FileAttribute[_]] =
    PlatformMetadata.Attributes.Permissions.asAttributes(platform, "rwx------")
}

object PlatformMetadata {
  lazy val current: PlatformMetadata = getPlatformMetadataFor(System.getProperty("os.name"))

  def getPlatformMetadataFor(osName: String): PlatformMetadata =
    if (osName.toLowerCase.contains("windows")) {
      Windows
    } else {
      Posix
    }

  object Posix extends PlatformMetadata {
    override val platform: Type = Type.Posix
    override val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  }

  object Windows extends PlatformMetadata {
    override val platform: Type = Type.Windows
    override val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  }

  sealed trait Type {
    override def toString: String = getClass.getSimpleName.stripSuffix("$")
    def matches(permissions: String): Boolean
  }

  object Type {
    case object Posix extends Type {
      override def matches(permissions: String): Boolean =
        permissions.length == 9 && !permissions.contains(":")
    }

    case object Windows extends Type {
      override def matches(permissions: String): Boolean =
        permissions.startsWith(Attributes.Permissions.Windows.Prefix)
    }
  }

  final case class Defaults(
    permissions: Map[Type, Defaults.Permissions],
    currentUser: String
  ) {
    require(currentUser != null && !currentUser.isBlank, s"Invalid current user provided: [$currentUser]")

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def permissionsFor(platform: Type): Defaults.Permissions =
      permissions.get(platform) match {
        case Some(value) => value
        case None        => throw new IllegalArgumentException(s"Unexpected platform type provided: [${platform.toString}]")
      }
  }

  object Defaults {
    def default(): Defaults =
      Defaults(
        permissions = Map(
          Type.Posix -> Defaults.Permissions(
            files = "rw-------",
            directories = "rwx------"
          ),
          Type.Windows -> Defaults.Permissions(
            files = "",
            directories = ""
          )
        ),
        currentUser = System.getProperty("user.name")
      )

    def apply(config: com.typesafe.config.Config): Defaults =
      Defaults(
        permissions = Map(
          Type.Posix -> Defaults.Permissions(
            files = config.getString("posix.files"),
            directories = config.getString("posix.directories")
          ),
          Type.Windows -> Defaults.Permissions(
            files = config.getString("windows.files"),
            directories = config.getString("windows.directories")
          )
        ),
        currentUser = System.getProperty("user.name")
      )

    final case class Permissions(
      files: String,
      directories: String
    )
  }

  object Attributes {
    final case class Basic(
      isDirectory: Boolean,
      link: Option[Path],
      isHidden: Boolean,
      created: Instant,
      updated: Instant
    )

    object Basic {
      def extractFrom(entity: Path, attributes: BasicFileAttributes): Basic = Basic(
        isDirectory = attributes.isDirectory,
        link = if (Files.isSymbolicLink(entity)) Some(Files.readSymbolicLink(entity)) else None,
        isHidden = Files.isHidden(entity),
        created = attributes.creationTime.toInstant.truncatedTo(ChronoUnit.SECONDS),
        updated = attributes.lastModifiedTime.toInstant.truncatedTo(ChronoUnit.SECONDS)
      )

      def applyTo(attributes: BasicFileAttributeView, created: Instant, updated: Instant): Unit =
        attributes.setTimes(
          /* lastModifiedTime */ FileTime.from(updated),
          /* lastAccessTime */ FileTime.from(Instant.now()),
          /* createTime */ FileTime.from(created)
        )
    }

    final case class Ownership(
      owner: String,
      group: String
    )

    object Ownership {
      def extractFrom(entity: Path, attributes: BasicFileAttributes): Ownership =
        attributes match {
          case posix: PosixFileAttributes =>
            Ownership(
              owner = posix.owner.getName,
              group = posix.group.getName
            )

          case _ =>
            Ownership(
              owner = Files.getOwner(entity, LinkOption.NOFOLLOW_LINKS).getName,
              group = ""
            )
        }

      def applyTo(entity: Path, attributes: FileAttributeView, owner: String, group: Option[String]): Unit = {
        val lookupService = entity.getFileSystem.getUserPrincipalLookupService

        attributes match {
          case posix: PosixFileAttributeView =>
            posix.setOwner(lookupService.lookupPrincipalByName(owner))
            group.foreach(g => posix.setGroup(lookupService.lookupPrincipalByGroupName(g)))

          case _ =>
            val _ = Files.setOwner(entity, lookupService.lookupPrincipalByName(owner))
        }
      }
    }

    object Permissions {
      def extractFrom(entity: Path, attributes: BasicFileAttributes): String =
        attributes match {
          case posix: PosixFileAttributes => PosixFilePermissions.toString(posix.permissions())
          case _                          => Windows.extractFrom(entity)
        }

      def applyTo(entity: Path, attributes: FileAttributeView, permissions: String): Unit =
        attributes match {
          case posix: PosixFileAttributeView => posix.setPermissions(PosixFilePermissions.fromString(permissions))
          case _                             => Windows.applyTo(entity, permissions)
        }

      def asAttributes(platform: Type, permissions: String): Seq[FileAttribute[_]] =
        platform match {
          case Type.Posix   => Seq(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions)))
          case Type.Windows => Seq.empty
        }

      object Windows {
        final val Prefix: String = "wacl:"

        @SuppressWarnings(Array("org.wartremover.warts.Throw"))
        def extractFrom(entity: Path): String = {
          val attributes = Files.getFileAttributeView(entity, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)
          if (attributes != null) {
            val entries = attributes.getAcl.asScala
            val serialized = entries
              .map { entry =>
                val entryType = entry.`type`().name()
                val entryPrincipal = entry.principal().getName
                val entryPermissions = entry.permissions().asScala.map(_.name()).toSeq.sorted.mkString(",")
                val entryFlags = entry.flags().asScala.map(_.name()).toSeq.sorted.mkString(",")

                if (entryFlags.nonEmpty) {
                  s"$entryType;$entryPrincipal;$entryPermissions;$entryFlags"
                } else {
                  s"$entryType;$entryPrincipal;$entryPermissions"
                }
              }
              .mkString("|")

            if (serialized.nonEmpty) s"$Prefix$serialized" else ""
          } else {
            throw new IllegalArgumentException(s"Failed to get attribute view for [${entity.toString}]")
          }
        }

        @SuppressWarnings(Array("org.wartremover.warts.Throw"))
        def applyTo(entity: Path, permissions: String): Unit =
          if (permissions.startsWith(Prefix) || permissions.isBlank) {
            val attributes = Files.getFileAttributeView(entity, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)
            if (attributes != null) {
              val acl = permissions.stripPrefix(Prefix)

              if (acl.isBlank) {
                // do nothing; inherit from parent
              } else {
                val lookupService = entity.getFileSystem.getUserPrincipalLookupService

                val entries = acl
                  .split("\\|")
                  .filter(_.nonEmpty)
                  .map { entry =>
                    entry.split(";").toList match {
                      case providedType :: providedPrincipal :: providedPermissions :: remaining =>
                        val entryType = AclEntryType.valueOf(providedType)

                        val entryPrincipal = lookupService.lookupPrincipalByName(providedPrincipal)

                        val entryPermissions = providedPermissions
                          .split(",")
                          .filter(_.nonEmpty)
                          .map(AclEntryPermission.valueOf)
                          .toSet

                        val entryFlags = remaining
                          .take(1)
                          .flatMap(_.split(",").filter(_.nonEmpty).map(AclEntryFlag.valueOf))
                          .toSet

                        AclEntry
                          .newBuilder()
                          .setType(entryType)
                          .setPrincipal(entryPrincipal)
                          .setPermissions(entryPermissions.asJava)
                          .setFlags(entryFlags.asJava)
                          .build()

                      case _ =>
                        throw new IllegalArgumentException(
                          s"Invalid permissions provided for [${entity.toString}]: [$permissions]"
                        )
                    }
                  }
                  .toList

                attributes.setAcl(entries.asJava)
              }
            } else {
              throw new IllegalArgumentException(s"Failed to get attribute view for [${entity.toString}]")
            }
          } else {
            throw new IllegalArgumentException(s"Invalid permissions provided for [${entity.toString}]: [$permissions]")

          }
      }
    }
  }

  private[stasis] def attributesFor(platform: Type, entity: Path): BasicFileAttributes =
    Files.readAttributes(
      /* path */ entity,
      /* type */ platform match {
        case Type.Posix   => classOf[PosixFileAttributes]
        case Type.Windows => classOf[BasicFileAttributes]
      },
      /* options */ LinkOption.NOFOLLOW_LINKS
    )

  private[stasis] def attributeViewFor(platform: Type, entity: Path): BasicFileAttributeView =
    Files.getFileAttributeView(
      /* path */ entity,
      /* type */ platform match {
        case Type.Posix   => classOf[PosixFileAttributeView]
        case Type.Windows => classOf[BasicFileAttributeView]
      },
      /* options */ LinkOption.NOFOLLOW_LINKS
    )
}
