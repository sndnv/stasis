package stasis.test.specs.unit.client.analysis

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute._
import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.jdk.CollectionConverters._

import com.google.common.jimfs.Configuration
import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup

import stasis.client.analysis.PlatformMetadata
import stasis.client.model.EntityMetadata
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class PlatformMetadataSpec extends AsyncUnitSpec with ResourceHelpers {
  import PlatformMetadataSpec.Setup

  "A PlatformMetadata Type" should "match POSIX permissions" in {
    PlatformMetadata.Type.Posix.matches("rwxr-xr-x") should be(true)
    PlatformMetadata.Type.Posix.matches("rw-------") should be(true)
    PlatformMetadata.Type.Posix.matches("") should be(false)
    PlatformMetadata.Type.Posix.matches("wacl:ALLOW;user;READ_DATA") should be(false)
    PlatformMetadata.Type.Posix.matches("other") should be(false)
  }

  it should "match Windows permissions" in {
    PlatformMetadata.Type.Windows.matches("wacl:ALLOW;user;READ_DATA") should be(true)
    PlatformMetadata.Type.Windows.matches("wacl:") should be(true)
    PlatformMetadata.Type.Windows.matches("rwxr-xr-x") should be(false)
    PlatformMetadata.Type.Windows.matches("") should be(false)
    PlatformMetadata.Type.Windows.matches("other") should be(false)
  }

  "PlatformMetadata" should "support creation based on OS name" in {
    PlatformMetadata.forOperatingSystem(osName = "Windows") should be(PlatformMetadata.Windows)
    PlatformMetadata.forOperatingSystem(osName = "Linux") should be(PlatformMetadata.Posix)
    PlatformMetadata.forOperatingSystem(osName = "Mac OS X") should be(PlatformMetadata.Posix)
    PlatformMetadata.forOperatingSystem(osName = "AIX") should be(PlatformMetadata.Posix)
    PlatformMetadata.forOperatingSystem(osName = "Other") should be(PlatformMetadata.Posix)
  }

  it should "support creation based on a file system" in {
    val (unixFilesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)
    val (macosFilesystem, _) = createMockFileSystem(setup = FileSystemSetup.MacOS)
    val (windowsFilesystem, _) = createMockFileSystem(setup = FileSystemSetup.Windows)

    PlatformMetadata.forFileSystem(unixFilesystem) should be(PlatformMetadata.Posix)
    PlatformMetadata.forFileSystem(macosFilesystem) should be(PlatformMetadata.Posix)
    PlatformMetadata.forFileSystem(windowsFilesystem) should be(PlatformMetadata.Windows)

    PlatformMetadata.forFileSystem(FileSystems.getDefault) should be(
      PlatformMetadata.forOperatingSystem(System.getProperty("os.name"))
    )
  }

  "PlatformMetadata Defaults" should "be created with default values" in {
    val defaults = PlatformMetadata.Defaults.default()

    defaults.permissionsFor(PlatformMetadata.Type.Posix).files should be("rw-------")
    defaults.permissionsFor(PlatformMetadata.Type.Posix).directories should be("rwx------")
    defaults.permissionsFor(PlatformMetadata.Type.Windows).files should be("")
    defaults.permissionsFor(PlatformMetadata.Type.Windows).directories should be("")
    defaults.currentUser should not be empty
  }

  they should "be created from config" in {
    val config = com.typesafe.config.ConfigFactory.parseString(
      """
        |posix.files = "rw-r--r--"
        |posix.directories = "rwxr-xr-x"
        |windows.files = "wacl:ALLOW;user;READ_DATA"
        |windows.directories = "wacl:ALLOW;user;READ_DATA,EXECUTE"
        |""".stripMargin
    )

    val defaults = PlatformMetadata.Defaults(config)

    defaults.permissionsFor(PlatformMetadata.Type.Posix).files should be("rw-r--r--")
    defaults.permissionsFor(PlatformMetadata.Type.Posix).directories should be("rwxr-xr-x")
    defaults.permissionsFor(PlatformMetadata.Type.Windows).files should be("wacl:ALLOW;user;READ_DATA")
    defaults.permissionsFor(PlatformMetadata.Type.Windows).directories should be("wacl:ALLOW;user;READ_DATA,EXECUTE")
  }

  they should "fail if Posix permissions are missing" in {
    an[IllegalArgumentException] should be thrownBy PlatformMetadata
      .Defaults(
        permissions = Map(
          PlatformMetadata.Type.Windows -> PlatformMetadata.Defaults.Permissions(files = "", directories = "")
        ),
        currentUser = "test-user"
      )
      .permissionsFor(platform = PlatformMetadata.Type.Posix)
  }

  they should "fail if Windows permissions are missing" in {
    an[IllegalArgumentException] should be thrownBy PlatformMetadata
      .Defaults(
        permissions = Map(
          PlatformMetadata.Type.Posix -> PlatformMetadata.Defaults.Permissions(files = "rw-------", directories = "rwx------")
        ),
        currentUser = "test-user"
      )
      .permissionsFor(platform = PlatformMetadata.Type.Windows)
  }

  they should "fail if current user is blank" in {
    an[IllegalArgumentException] should be thrownBy PlatformMetadata.Defaults(
      permissions = Map(
        PlatformMetadata.Type.Posix -> PlatformMetadata.Defaults.Permissions(files = "rw-------", directories = "rwx------"),
        PlatformMetadata.Type.Windows -> PlatformMetadata.Defaults.Permissions(files = "", directories = "")
      ),
      currentUser = ""
    )
  }

  they should "provide permissions based on platform" in {
    val posix = PlatformMetadata.Defaults.Permissions(files = "rw-rw-rw-", directories = "rwx--x--x")
    val windows = PlatformMetadata.Defaults.Permissions(files = "", directories = "")

    val defaults = PlatformMetadata.Defaults(
      permissions = Map(
        PlatformMetadata.Type.Posix -> posix,
        PlatformMetadata.Type.Windows -> windows
      ),
      currentUser = "test-user"
    )

    defaults.permissionsFor(platform = PlatformMetadata.Type.Windows) should be(windows)
    defaults.permissionsFor(platform = PlatformMetadata.Type.Posix) should be(posix)
  }

  manageAttributesFor(setup = Setup.Windows)
  manageAttributesFor(setup = Setup.MacOS)
  manageAttributesFor(setup = Setup.Unix)

  def manageAttributesFor(setup: FileSystemSetup): Unit = {
    s"PlatformMetadata Attributes (Basic / $setup)" should "extract attributes from a file" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      Files.createFile(file)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      val basic = PlatformMetadata.Attributes.Basic.extractFrom(file, attributes)

      basic.isDirectory should be(false)
      basic.link should be(None)
      basic.isHidden should be(false)
      basic.created should be > Instant.MIN
      basic.updated should be > Instant.MIN
    }

    they should "extract attributes from a directory" in {
      val (fs, _) = createMockFileSystem(setup)
      val directory = fs.getPath("test-directory")
      Files.createDirectory(directory)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      val basic = PlatformMetadata.Attributes.Basic.extractFrom(directory, attributes)

      basic.isDirectory should be(true)
      basic.link should be(None)
      basic.isHidden should be(false)
      basic.created should be > Instant.MIN
      basic.updated should be > Instant.MIN
    }

    they should "extract attributes from a symbolic link" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      val link = fs.getPath("test-link")
      Files.createFile(file)
      Files.createSymbolicLink(link, file)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = link)
      val basic = PlatformMetadata.Attributes.Basic.extractFrom(link, attributes)

      basic.isDirectory should be(false)
      basic.link should be(Some(file))
      basic.isHidden should be(false)
      basic.created should be > Instant.MIN
      basic.updated should be > Instant.MIN
    }

    they should "apply attributes to a file" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      Files.createFile(file)

      val original = PlatformMetadata.Attributes.Basic.extractFrom(
        entity = file,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      )

      original.isDirectory should be(false)
      original.link should be(None)
      original.isHidden should be(false)
      original.created should be > Instant.MIN
      original.updated should be > Instant.MIN

      val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

      PlatformMetadata.Attributes.Basic.applyTo(
        attributes = PlatformMetadata.attributeViewFor(platform = setup.asPlatform, entity = file),
        created = now,
        updated = now
      )

      val updated = PlatformMetadata.Attributes.Basic.extractFrom(
        entity = file,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      )

      updated.isDirectory should be(false)
      updated.link should be(None)
      updated.isHidden should be(false)
      updated.created should be(now)
      updated.updated should be(now)
    }

    they should "apply attributes to a directory" in {
      val (fs, _) = createMockFileSystem(setup)
      val directory = fs.getPath("test-file")
      Files.createDirectory(directory)

      val original = PlatformMetadata.Attributes.Basic.extractFrom(
        entity = directory,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      )

      original.isDirectory should be(true)
      original.link should be(None)
      original.isHidden should be(false)
      original.created should be > Instant.MIN
      original.updated should be > Instant.MIN

      val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

      PlatformMetadata.Attributes.Basic.applyTo(
        attributes = PlatformMetadata.attributeViewFor(platform = setup.asPlatform, entity = directory),
        created = now,
        updated = now
      )

      val updated = PlatformMetadata.Attributes.Basic.extractFrom(
        entity = directory,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      )

      updated.isDirectory should be(true)
      updated.link should be(None)
      updated.isHidden should be(false)
      updated.created should be(now)
      updated.updated should be(now)
    }

    s"PlatformMetadata Attributes (Ownership / $setup)" should "extract attributes from a file" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      Files.createFile(file)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      val ownership = PlatformMetadata.Attributes.Ownership.extractFrom(file, attributes)

      ownership.owner should be("user")

      if (setup.isWindows) {
        ownership.group should be("")
      } else {
        ownership.group should be("group")
      }
    }

    they should "extract attributes from a directory" in {
      val (fs, _) = createMockFileSystem(setup)
      val directory = fs.getPath("test-file")
      Files.createDirectory(directory)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      val ownership = PlatformMetadata.Attributes.Ownership.extractFrom(directory, attributes)

      ownership.owner should be("user")

      if (setup.isWindows) {
        ownership.group should be("")
      } else {
        ownership.group should be("group")
      }
    }

    they should "apply attributes to a file" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      Files.createFile(file)

      val original = PlatformMetadata.Attributes.Ownership.extractFrom(
        entity = file,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      )

      original.owner should be("user")

      if (setup.isWindows) {
        original.group should be("")
      } else {
        original.group should be("group")
      }

      PlatformMetadata.Attributes.Ownership.applyTo(
        entity = file,
        attributes = PlatformMetadata.attributeViewFor(platform = setup.asPlatform, entity = file),
        owner = "other-user",
        group = Some("other-group")
      )

      val updated = PlatformMetadata.Attributes.Ownership.extractFrom(
        entity = file,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      )

      updated.owner should be("other-user")

      if (setup.isWindows) {
        updated.group should be("")
      } else {
        updated.group should be("other-group")
      }
    }

    they should "apply attributes to a directory" in {
      val (fs, _) = createMockFileSystem(setup)
      val directory = fs.getPath("test-file")
      Files.createDirectory(directory)

      val original = PlatformMetadata.Attributes.Ownership.extractFrom(
        entity = directory,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      )

      original.owner should be("user")

      if (setup.isWindows) {
        original.group should be("")
      } else {
        original.group should be("group")
      }

      PlatformMetadata.Attributes.Ownership.applyTo(
        entity = directory,
        attributes = PlatformMetadata.attributeViewFor(platform = setup.asPlatform, entity = directory),
        owner = "other-user",
        group = Some("other-group")
      )

      val updated = PlatformMetadata.Attributes.Ownership.extractFrom(
        entity = directory,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      )

      updated.owner should be("other-user")

      if (setup.isWindows) {
        updated.group should be("")
      } else {
        updated.group should be("other-group")
      }
    }

    s"PlatformMetadata Attributes (Permissions / $setup)" should "extract permissions from a file" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      Files.createFile(file)

      setup.setPermissions(file)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      val permissions = PlatformMetadata.Attributes.Permissions.extractFrom(file, attributes)

      if (setup.isWindows) {
        permissions should be(windowsPermissions)
      } else {
        permissions should be(posixPermissions)
      }
    }

    they should "extract permissions from a directory" in {
      val (fs, _) = createMockFileSystem(setup)
      val directory = fs.getPath("test-directory")
      Files.createDirectory(directory)

      setup.setPermissions(directory)

      val attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      val permissions = PlatformMetadata.Attributes.Permissions.extractFrom(directory, attributes)

      if (setup.isWindows) {
        permissions should be(windowsPermissions)
      } else {
        permissions should be(posixPermissions)
      }
    }

    they should "apply permissions to a file" in {
      val (fs, _) = createMockFileSystem(setup)
      val file = fs.getPath("test-file")
      Files.createFile(file)

      setup.setPermissions(file)

      val original = PlatformMetadata.Attributes.Permissions.extractFrom(
        entity = file,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      )

      if (setup.isWindows) {
        original should be(windowsPermissions)
      } else {
        original should be(posixPermissions)
      }

      val updatedPosixPermissions = "--x--x--x"
      val updatedWindowsPermissions = "wacl:DENY;user;READ_DATA,WRITE_DATA"

      PlatformMetadata.Attributes.Permissions.applyTo(
        entity = file,
        attributes = PlatformMetadata.attributeViewFor(platform = setup.asPlatform, entity = file),
        permissions = if (setup.isWindows) {
          updatedWindowsPermissions
        } else {
          updatedPosixPermissions
        }
      )

      val updated = PlatformMetadata.Attributes.Permissions.extractFrom(
        entity = file,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = file)
      )

      if (setup.isWindows) {
        updated should be(updatedWindowsPermissions)
      } else {
        updated should be(updatedPosixPermissions)
      }
    }

    they should "apply permissions to a directory" in {
      val (fs, _) = createMockFileSystem(setup)
      val directory = fs.getPath("test-file")
      Files.createDirectory(directory)

      setup.setPermissions(directory)

      val original = PlatformMetadata.Attributes.Permissions.extractFrom(
        entity = directory,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      )

      if (setup.isWindows) {
        original should be(windowsPermissions)
      } else {
        original should be(posixPermissions)
      }

      val updatedPosixPermissions = "--x--x--x"
      val updatedWindowsPermissions = "wacl:DENY;user;READ_DATA,WRITE_DATA"

      PlatformMetadata.Attributes.Permissions.applyTo(
        entity = directory,
        attributes = PlatformMetadata.attributeViewFor(platform = setup.asPlatform, entity = directory),
        permissions = if (setup.isWindows) {
          updatedWindowsPermissions
        } else {
          updatedPosixPermissions
        }
      )

      val updated = PlatformMetadata.Attributes.Permissions.extractFrom(
        entity = directory,
        attributes = PlatformMetadata.attributesFor(platform = setup.asPlatform, entity = directory)
      )

      if (setup.isWindows) {
        updated should be(updatedWindowsPermissions)
      } else {
        updated should be(updatedPosixPermissions)
      }
    }
  }

  "PlatformMetadata Attributes (permissions)" should "produce file attributes (POSIX)" in {
    val attributes = PlatformMetadata.Attributes.Permissions.asAttributes(PlatformMetadata.Type.Posix, "rw-------")

    attributes should not be empty
  }

  they should "produce empty attributes (Windows)" in {
    val attributes = PlatformMetadata.Attributes.Permissions.asAttributes(PlatformMetadata.Type.Windows, "rw-------")

    attributes should be(empty)
  }

  "PlatformMetadata Attributes (permissions / Windows ACLs)" should "serialize ACL entries (without flags)" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    val owner = attributes.getOwner
    val entry = AclEntry
      .newBuilder()
      .setType(AclEntryType.ALLOW)
      .setPrincipal(owner)
      .setPermissions(Set(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA).asJava)
      .build()

    attributes.setAcl(java.util.List.of(entry))

    val serialized = PlatformMetadata.Attributes.Permissions.Windows.extractFrom(file)

    serialized should be("wacl:ALLOW;user;READ_DATA,WRITE_DATA")
  }

  they should "serialize ACL entries (with flags)" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Windows)
    val directory = fs.getPath("test-directory")
    Files.createDirectory(directory)

    val attributes = Files.getFileAttributeView(directory, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    val owner = attributes.getOwner
    val entry = AclEntry
      .newBuilder()
      .setType(AclEntryType.ALLOW)
      .setPrincipal(owner)
      .setPermissions(Set(AclEntryPermission.READ_DATA).asJava)
      .setFlags(Set(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).asJava)
      .build()

    attributes.setAcl(java.util.List.of(entry))

    val serialized = PlatformMetadata.Attributes.Permissions.Windows.extractFrom(directory)

    serialized should be("wacl:ALLOW;user;READ_DATA;DIRECTORY_INHERIT,FILE_INHERIT")
  }

  they should "deserialize and apply ACL entries (without flags)" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    val owner = attributes.getOwner
    val permissions = s"wacl:ALLOW;${owner.getName};READ_DATA,WRITE_DATA"

    PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, permissions)

    attributes.getAcl.asScala.toList match {
      case entry :: Nil =>
        entry.`type`() should be(AclEntryType.ALLOW)
        entry.principal() should be(owner)
        entry.permissions() should contain(AclEntryPermission.READ_DATA)
        entry.permissions() should contain(AclEntryPermission.WRITE_DATA)
        entry.flags() should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "deserialize and apply ACL entries (with flags)" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    val owner = attributes.getOwner
    val permissions = s"wacl:ALLOW;${owner.getName};READ_DATA,WRITE_DATA;DIRECTORY_INHERIT,FILE_INHERIT"

    PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, permissions)

    attributes.getAcl.asScala.toList match {
      case entry :: Nil =>
        entry.`type`() should be(AclEntryType.ALLOW)
        entry.principal() should be(owner)
        entry.permissions() should contain(AclEntryPermission.READ_DATA)
        entry.permissions() should contain(AclEntryPermission.WRITE_DATA)

        entry.flags() should contain(AclEntryFlag.DIRECTORY_INHERIT)
        entry.flags() should contain(AclEntryFlag.FILE_INHERIT)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "consistently extract and apply ACL entries" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    val owner = attributes.getOwner
    val original = AclEntry
      .newBuilder()
      .setType(AclEntryType.ALLOW)
      .setPrincipal(owner)
      .setPermissions(
        Set(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA, AclEntryPermission.EXECUTE).asJava
      )
      .build()

    attributes.setAcl(java.util.List.of(original))

    val serialized = PlatformMetadata.Attributes.Permissions.Windows.extractFrom(file)
    PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, serialized)

    attributes.getAcl.asScala.toList match {
      case entry :: Nil =>
        entry.`type`() should be(original.`type`())
        entry.principal() should be(original.principal())
        entry.permissions() should be(original.permissions())
        entry.flags() should be(original.flags())

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "consistently extract and apply multiple ACL entries" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    val owner = attributes.getOwner
    val allowEntry = AclEntry
      .newBuilder()
      .setType(AclEntryType.ALLOW)
      .setPrincipal(owner)
      .setPermissions(Set(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA).asJava)
      .setFlags(Set(AclEntryFlag.FILE_INHERIT).asJava)
      .build()

    val denyEntry = AclEntry
      .newBuilder()
      .setType(AclEntryType.DENY)
      .setPrincipal(owner)
      .setPermissions(Set(AclEntryPermission.EXECUTE).asJava)
      .build()

    attributes.setAcl(java.util.List.of(allowEntry, denyEntry))

    val serialized = PlatformMetadata.Attributes.Permissions.Windows.extractFrom(file)
    serialized should be("wacl:ALLOW;user;READ_DATA,WRITE_DATA;FILE_INHERIT|DENY;user;EXECUTE")

    PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, serialized)

    attributes.getAcl.asScala.toList match {
      case first :: second :: Nil =>
        first.`type`() should be(AclEntryType.ALLOW)
        first.principal() should be(owner)
        first.permissions() should be(Set(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA).asJava)
        first.flags() should be(Set(AclEntryFlag.FILE_INHERIT).asJava)

        second.`type`() should be(AclEntryType.DENY)
        second.principal() should be(owner)
        second.permissions() should be(Set(AclEntryPermission.EXECUTE).asJava)
        second.flags() should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "not apply ACLs if blank permissions are provided" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)
    val owner = attributes.getOwner
    val entry = AclEntry
      .newBuilder()
      .setType(AclEntryType.ALLOW)
      .setPrincipal(owner)
      .setPermissions(Set(AclEntryPermission.READ_DATA).asJava)
      .build()

    attributes.setAcl(java.util.List.of(entry))

    PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, "")

    attributes.getAcl.asScala.toList match {
      case existing :: Nil =>
        existing.`type`() should be(AclEntryType.ALLOW)
        existing.permissions() should be(Set(AclEntryPermission.READ_DATA).asJava)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "not apply ACLs if only the prefix is provided" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val attributes = Files.getFileAttributeView(file, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)
    val owner = attributes.getOwner
    val entry = AclEntry
      .newBuilder()
      .setType(AclEntryType.ALLOW)
      .setPrincipal(owner)
      .setPermissions(Set(AclEntryPermission.READ_DATA).asJava)
      .build()

    attributes.setAcl(java.util.List.of(entry))

    PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, "wacl:")

    attributes.getAcl.asScala.toList match {
      case existing :: Nil =>
        existing.`type`() should be(AclEntryType.ALLOW)
        existing.permissions() should be(Set(AclEntryPermission.READ_DATA).asJava)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "fail to apply ACLs if non-Windows permissions are provided" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val e = intercept[IllegalArgumentException](PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, "rwxr-xr-x"))
    e.getMessage should be("Invalid permissions provided for [test-file]: [rwxr-xr-x]")
  }

  they should "fail to apply ACLs if unexpected permissions are provided" in {
    val (fs, _) = createMockFileSystem(setup = Setup.Windows)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val e = intercept[IllegalArgumentException](PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, "wacl:ALLOW"))
    e.getMessage should be("Invalid permissions provided for [test-file]: [wacl:ALLOW]")
  }

  they should "fail to apply ACLs if the filesystem does not support them" in {
    val (fs, _) = createMockFileSystem(setup = Setup.MacOS)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val e = intercept[IllegalArgumentException](
      PlatformMetadata.Attributes.Permissions.Windows.applyTo(file, "wacl:ALLOW;user;READ_DATA,WRITE_DATA")
    )
    e.getMessage should be("Failed to get attribute view for [test-file]")
  }

  they should "fail to extract ACLs if the filesystem does not support them" in {
    val (fs, _) = createMockFileSystem(setup = Setup.MacOS)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val e = intercept[IllegalArgumentException](
      PlatformMetadata.Attributes.Permissions.Windows.extractFrom(file)
    )
    e.getMessage should be("Failed to get attribute view for [test-file]")
  }

  "Posix PlatformMetadata" should "extract metadata from a file" in {
    val setup = FileSystemSetup.Unix
    val (fs, _) = createMockFileSystem(setup)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    setup.setPermissions(file)

    PlatformMetadata.Posix
      .extractFrom(file)
      .map { metadata =>
        metadata.path should be(file)
        metadata.isDirectory should be(false)
        metadata.link should be(None)
        metadata.isHidden should be(false)
        metadata.owner should not be empty
        metadata.group should not be empty
        metadata.permissions should be("rwxr-x---")
      }
  }

  it should "extract metadata from a directory" in {
    val setup = FileSystemSetup.Unix
    val (fs, _) = createMockFileSystem(setup)
    val directory = fs.getPath("test-directory")
    Files.createDirectory(directory)

    PlatformMetadata.Posix
      .extractFrom(directory)
      .map { metadata =>
        metadata.path should be(directory)
        metadata.isDirectory should be(true)
        metadata.owner should not be empty
        metadata.group should not be empty
        metadata.permissions should not be empty
      }
  }

  it should "apply POSIX metadata to a file" in {
    implicit val defaults: PlatformMetadata.Defaults = PlatformMetadata.Defaults.default()

    val setup = FileSystemSetup.Unix
    val (fs, _) = createMockFileSystem(setup)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    setup.setPermissions(file)

    val metadata = EntityMetadata.File(
      path = file.toAbsolutePath.toString,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = "user",
      group = "group",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crates = Map.empty,
      compression = "none"
    )

    for {
      _ <- PlatformMetadata.Posix.applyTo(file, metadata)
      result <- PlatformMetadata.Posix.extractFrom(file)
    } yield {
      result.permissions should be("rwxrwxrwx")
      result.updated should be(metadata.updated)
      result.owner should be(metadata.owner)
      result.group should be(metadata.group)
    }
  }

  it should "apply default permissions for non-POSIX metadata" in {
    val currentUser = System.getProperty("user.name")

    implicit val defaults: PlatformMetadata.Defaults = PlatformMetadata.Defaults(
      permissions = Map(
        PlatformMetadata.Type.Posix -> PlatformMetadata.Defaults.Permissions(
          files = "rw-r--r--",
          directories = "rwxr-xr-x"
        ),
        PlatformMetadata.Type.Windows -> PlatformMetadata.Defaults.Permissions(files = "", directories = "")
      ),
      currentUser = currentUser
    )

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Unix)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val metadata = EntityMetadata.File(
      path = file.toAbsolutePath.toString,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = "some-windows-user",
      group = "",
      permissions = "wacl:ALLOW;windows-user;READ_DATA",
      checksum = BigInt(1),
      crates = Map.empty,
      compression = "none"
    )

    for {
      _ <- PlatformMetadata.Posix.applyTo(file, metadata)
      result <- PlatformMetadata.Posix.extractFrom(file)
    } yield {
      result.permissions should be("rw-r--r--")
      result.owner should be(currentUser)
      result.updated should be(metadata.updated)
    }
  }

  it should "apply default permissions for non-POSIX directory metadata" in {
    val currentUser = System.getProperty("user.name")

    implicit val defaults: PlatformMetadata.Defaults = PlatformMetadata.Defaults(
      permissions = Map(
        PlatformMetadata.Type.Posix -> PlatformMetadata.Defaults.Permissions(
          files = "rw-r--r--",
          directories = "rwxr-xr-x"
        ),
        PlatformMetadata.Type.Windows -> PlatformMetadata.Defaults.Permissions(files = "", directories = "")
      ),
      currentUser = currentUser
    )

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Unix)
    val directory = fs.getPath("test-directory")
    Files.createDirectory(directory)

    val metadata = EntityMetadata.Directory(
      path = directory.toAbsolutePath.toString,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = "some-windows-user",
      group = "",
      permissions = "wacl:ALLOW;windows-user;READ_DATA"
    )

    for {
      _ <- PlatformMetadata.Posix.applyTo(directory, metadata)
      result <- PlatformMetadata.Posix.extractFrom(directory)
    } yield {
      result.permissions should be("rwxr-xr-x")
      result.owner should be(currentUser)
      result.updated should be(metadata.updated)
    }
  }

  it should "provide owner-only file attributes" in {
    val attributes = PlatformMetadata.Posix.ownerOnlyFileAttributes.map(_.value()).mkString(",")

    attributes should include("OWNER_READ")
    attributes should include("OWNER_WRITE")
  }

  it should "provide owner-only directory attributes" in {
    val attributes = PlatformMetadata.Posix.ownerOnlyDirectoryAttributes.map(_.value().toString).mkString(",")

    attributes should include("OWNER_READ")
    attributes should include("OWNER_WRITE")
    attributes should include("OWNER_EXECUTE")
  }

  "Windows PlatformMetadata" should "extract metadata from a file" in {
    val setup = FileSystemSetup.Windows
    val (fs, _) = createMockFileSystem(setup)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    setup.setPermissions(file)

    PlatformMetadata.Windows
      .extractFrom(file)
      .map { metadata =>
        metadata.path should be(file)
        metadata.isDirectory should be(false)
        metadata.link should be(None)
        metadata.isHidden should be(false)
        metadata.owner should not be empty
        metadata.group should be(empty)
        metadata.permissions should be("wacl:ALLOW;user;READ_DATA,WRITE_DATA")
      }
  }

  it should "extract metadata from a directory" in {
    val setup = FileSystemSetup.Windows
    val (fs, _) = createMockFileSystem(setup)
    val directory = fs.getPath("test-directory")
    Files.createDirectory(directory)

    PlatformMetadata.Windows
      .extractFrom(directory)
      .map { metadata =>
        metadata.path should be(directory)
        metadata.isDirectory should be(true)
        metadata.owner should not be empty
        metadata.group should be(empty)
        metadata.permissions should be("")
      }
  }

  it should "apply Windows metadata to a file" in {
    implicit val defaults: PlatformMetadata.Defaults = PlatformMetadata.Defaults.default()

    val setup = FileSystemSetup.Windows
    val (fs, _) = createMockFileSystem(setup)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    setup.setPermissions(file)

    val metadata = EntityMetadata.File(
      path = file.toAbsolutePath.toString,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = "user",
      group = "",
      permissions = "wacl:ALLOW;user;READ_DATA,WRITE_DATA",
      checksum = BigInt(1),
      crates = Map.empty,
      compression = "none"
    )

    for {
      _ <- PlatformMetadata.Windows.applyTo(file, metadata)
      result <- PlatformMetadata.Windows.extractFrom(file)
    } yield {
      result.permissions should be("wacl:ALLOW;user;READ_DATA,WRITE_DATA")
      result.updated should be(metadata.updated)
      result.owner should be(metadata.owner)
      result.group should be(metadata.group)
    }
  }

  it should "apply default permissions for non-Windows metadata" in {
    val currentUser = System.getProperty("user.name")

    implicit val defaults: PlatformMetadata.Defaults = PlatformMetadata.Defaults(
      permissions = Map(
        PlatformMetadata.Type.Posix -> PlatformMetadata.Defaults.Permissions(files = "", directories = ""),
        PlatformMetadata.Type.Windows -> PlatformMetadata.Defaults.Permissions(files = "", directories = "")
      ),
      currentUser = currentUser
    )

    val setup = FileSystemSetup.Windows
    val (fs, _) = createMockFileSystem(setup)
    val file = fs.getPath("test-file")
    Files.createFile(file)

    val metadata = EntityMetadata.File(
      path = file.toAbsolutePath.toString,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = "some-windows-user",
      group = "",
      permissions = "rwx------",
      checksum = BigInt(1),
      crates = Map.empty,
      compression = "none"
    )

    for {
      _ <- PlatformMetadata.Windows.applyTo(file, metadata)
      result <- PlatformMetadata.Windows.extractFrom(file)
    } yield {
      result.permissions should be("")
      result.owner should be(currentUser)
      result.updated should be(metadata.updated)
    }
  }

  it should "apply default permissions for non-Windows directory metadata" in {
    val currentUser = System.getProperty("user.name")

    implicit val defaults: PlatformMetadata.Defaults = PlatformMetadata.Defaults(
      permissions = Map(
        PlatformMetadata.Type.Posix -> PlatformMetadata.Defaults.Permissions(files = "", directories = ""),
        PlatformMetadata.Type.Windows -> PlatformMetadata.Defaults.Permissions(files = "", directories = "")
      ),
      currentUser = currentUser
    )

    val setup = FileSystemSetup.Windows
    val (fs, _) = createMockFileSystem(setup)
    val directory = fs.getPath("test-directory")
    Files.createDirectory(directory)

    val metadata = EntityMetadata.Directory(
      path = directory.toAbsolutePath.toString,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = "some-posix-user",
      group = "some-group",
      permissions = "rwx------"
    )

    for {
      _ <- PlatformMetadata.Windows.applyTo(directory, metadata)
      result <- PlatformMetadata.Windows.extractFrom(directory)
    } yield {
      result.permissions should be("")
      result.owner should be(currentUser)
      result.updated should be(metadata.updated)
    }
  }

  it should "provide owner-only file attributes" in {
    val attributes = PlatformMetadata.Windows.ownerOnlyFileAttributes
    attributes should be(empty)
  }

  it should "provide owner-only directory attributes" in {
    val attributes = PlatformMetadata.Windows.ownerOnlyDirectoryAttributes
    attributes should be(empty)
  }

  private val posixPermissions: String = "rwxr-x---"
  private val windowsPermissions: String = "wacl:ALLOW;user;READ_DATA,WRITE_DATA"

  private implicit class ExtendedFileSystemSetup(setup: FileSystemSetup) {
    def asPlatform: PlatformMetadata.Type =
      if (isWindows) {
        PlatformMetadata.Type.Windows
      } else {
        PlatformMetadata.Type.Posix
      }

    def setPermissions(entity: Path): Unit =
      if (setup.isWindows) {
        val attributes = Files.getFileAttributeView(entity, classOf[AclFileAttributeView], LinkOption.NOFOLLOW_LINKS)
        val owner = attributes.getOwner
        val entry = AclEntry
          .newBuilder()
          .setType(AclEntryType.ALLOW)
          .setPrincipal(owner)
          .setPermissions(Set(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA).asJava)
          .build()

        attributes.setAcl(java.util.List.of(entry))
      } else {
        Files.setPosixFilePermissions(entity, PosixFilePermissions.fromString(posixPermissions))
      }

    def isWindows: Boolean = setup.name == "Windows"
  }
}

object PlatformMetadataSpec {
  object Setup {
    val Windows: FileSystemSetup = FileSystemSetup(
      name = "Windows",
      config = Configuration.windows().toBuilder.setAttributeViews("basic", "owner", "dos", "acl", "user").build(),
      chars = FileSystemSetup.Chars.Default,
      disallowedChars = (0 to 31).map(_.toChar) ++ Seq(
        '<', '>', ':', '"', '/', '\\', '|', '?', '*', ' '
      ),
      disallowedFileNames = Seq(" ", "."),
      maxFilesPerDir = 1,
      nestedParentDirs = 1,
      caseSensitive = false
    )

    val MacOS: FileSystemSetup = FileSystemSetup(
      name = "MacOS",
      config = Configuration.osX().toBuilder.setAttributeViews("basic", "posix").build(),
      chars = FileSystemSetup.Chars.Default,
      disallowedChars = Seq('\u0000', '/', '\n', '\r'),
      disallowedFileNames = Seq(".", ".."),
      maxFilesPerDir = 1,
      nestedParentDirs = 1,
      caseSensitive = false
    )

    val Unix: FileSystemSetup = FileSystemSetup(
      name = "Unix",
      config = Configuration.unix().toBuilder.setAttributeViews("basic", "posix").build(),
      chars = FileSystemSetup.Chars.Default,
      disallowedChars = Seq('\u0000', '/', '\n', '\r'),
      disallowedFileNames = Seq(".", ".."),
      maxFilesPerDir = 1,
      nestedParentDirs = 1,
      caseSensitive = true
    )
  }
}
