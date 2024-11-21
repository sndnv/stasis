package stasis.test.specs.unit.client.service

import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

import org.apache.pekko.util.ByteString

import stasis.client.service.ApplicationDirectory
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class ApplicationDirectorySpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApplicationDirectory" should "find individual files in any configuration location (config)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
      }
    )

    val actualFile = directory.findFile(file = targetFile)
    actualFile should not be empty
    actualFile should be(directory.config.map(_.resolve(targetFile).toAbsolutePath))
  }

  it should "find individual files in any configuration location (current)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.current.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
      }
    )

    val actualFile = directory.findFile(file = targetFile)
    actualFile should not be empty
    actualFile should be(directory.current.map(_.resolve(targetFile).toAbsolutePath))
  }

  it should "find individual files in any configuration location (user)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.user.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
      }
    )

    val actualFile = directory.findFile(file = targetFile)
    actualFile should not be empty
    actualFile should be(directory.user.map(_.resolve(targetFile).toAbsolutePath))
  }

  it should "find files based on a pattern in any configuration location" in {
    val otherFile1 = "other-file-1"
    val otherFile2 = "other-file-2"
    val otherFile3 = "other-file-3"
    val otherFile4 = "other-file-4"

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        val subdir1 = path.resolve("subdir1")
        val subdir2 = subdir1.resolve("subdir2")

        Files.createDirectories(path)
        Files.createDirectories(subdir1)
        Files.createDirectories(subdir2)

        Files.createFile(path.resolve(targetFile))
        Files.createFile(path.resolve(otherFile1))
        Files.createFile(path.resolve(otherFile2))
        Files.createFile(subdir1.resolve(otherFile3))
        Files.createFile(subdir2.resolve(otherFile4))
      }
    )

    withClue("files in the current configuration location") {
      val actualFiles = directory.findFiles(pattern = "other-*")
      actualFiles should not be empty
      actualFiles should be(
        Seq(
          directory.config.map(_.resolve(otherFile1).toAbsolutePath),
          directory.config.map(_.resolve(otherFile2).toAbsolutePath)
        ).flatten
      )
    }

    withClue("files in a subdirectory") {
      val actualFiles = directory.findFiles(pattern = "subdir1/other-*")
      actualFiles should not be empty
      actualFiles should be(
        Seq(
          directory.config.map(_.resolve(s"subdir1/$otherFile3").toAbsolutePath)
        ).flatten
      )
    }

    withClue("files in any subdirectory") {
      val actualFiles = directory.findFiles(pattern = "**/*file*")
      actualFiles should not be empty
      actualFiles.sorted should be(
        Seq(
          directory.config.map(_.resolve(targetFile).toAbsolutePath),
          directory.config.map(_.resolve(otherFile1).toAbsolutePath),
          directory.config.map(_.resolve(otherFile2).toAbsolutePath),
          directory.config.map(_.resolve(s"subdir1/$otherFile3").toAbsolutePath),
          directory.config.map(_.resolve(s"subdir1/subdir2/$otherFile4").toAbsolutePath)
        ).flatten.sorted
      )
    }
  }

  it should "find individual required files in any configuration location" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
      }
    )

    directory
      .requireFile(file = targetFile)
      .map { actualFile =>
        Some(actualFile) should be(directory.config.map(_.resolve(targetFile).toAbsolutePath))
      }
  }

  it should "fail if a required individual file is not found" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    directory
      .requireFile(file = targetFile)
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover { case e: FileNotFoundException =>
        e.getMessage should startWith(s"File [$targetFile] not found")
      }
  }

  it should "require files based on a pattern in any configuration location" in {
    val otherFile1 = "other-file-1"
    val otherFile2 = "other-file-2"

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
        Files.createFile(path.resolve(otherFile1))
        Files.createFile(path.resolve(otherFile2))
      }
    )

    directory.requireFiles(pattern = "other-*").map { actualFiles =>
      actualFiles should not be empty
      actualFiles should be(
        Seq(
          directory.config.map(_.resolve(otherFile1).toAbsolutePath),
          directory.config.map(_.resolve(otherFile2).toAbsolutePath)
        ).flatten
      )
    }
  }

  it should "fail if required files are not found" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    directory
      .requireFiles(pattern = "other-*")
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover { case e: FileNotFoundException =>
        e.getMessage should startWith("No files matching [other-*] were found")
      }
  }

  it should "handle missing configuration locations" in {
    val directory = createApplicationDirectory(init = _ => ())

    directory
      .requireFile(file = targetFile)
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover { case e: FileNotFoundException =>
        e.getMessage should startWith(s"File [$targetFile] not found")
      }
  }

  it should "pull data from files" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        val file = path.resolve(targetFile)
        Files.createDirectories(path)
        Files.createFile(file)
        Files.writeString(file, targetFileContent)
      }
    )

    directory
      .pullFile[String](file = targetFile)
      .map { actualContent =>
        actualContent should be(targetFileContent)
      }
  }

  it should "push data to files (file does not exist)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
      }
    )

    directory
      .pushFile[String](file = targetFile, content = targetFileContent)
      .map { path =>
        Some(path) should be(directory.config.map(_.resolve(targetFile).toAbsolutePath))
        Files.readString(path) should be(targetFileContent)
      }
  }

  it should "push data to files (file exists)" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    directory
      .pushFile[String](file = targetFile, content = targetFileContent)
      .map { path =>
        Some(path) should be(directory.config.map(_.resolve(targetFile).toAbsolutePath))
        Files.readString(path) should be(targetFileContent)
      }
  }

  it should "fail to push data if no configuration location is available" in {
    val directory = createApplicationDirectory(init = _ => ())

    directory
      .pushFile[String](file = targetFile, content = targetFileContent)
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover { case e: IllegalStateException =>
        e.getMessage should be(s"File [$targetFile] could not be created; no suitable directory available")
      }
  }

  it should "provide a configuration directory" in {
    val directory = createApplicationDirectory(init = _ => ())
    directory.configDirectory.map(_.toString) should not be empty
  }

  it should "provide an app directory (user)" in {
    val directory = ApplicationDirectory.Default.provideAppDirectory(
      applicationName = "test-app",
      userDirectory = Some(Paths.get("/tmp/test/user")),
      configDirectory = Some(Paths.get("/tmp/test/config"))
    )

    directory.toString should be("/tmp/test/user/test-app")
  }

  it should "provide an app directory (config)" in {
    val directory = ApplicationDirectory.Default.provideAppDirectory(
      applicationName = "test-app",
      userDirectory = None,
      configDirectory = Some(Paths.get("/tmp/test/config"))
    )

    directory.toString should be("/tmp/test/config/test-app")
  }

  it should "fail if no suitable app directory is available" in {
    an[IllegalStateException] should be thrownBy {
      ApplicationDirectory.Default.provideAppDirectory(
        applicationName = "test-app",
        userDirectory = None,
        configDirectory = None
      )
    }
  }

  private val targetFile = "test-file"
  private val targetFileContent = "test-content-line-01\ntest-content-line-02"

  private implicit val stringToByteString: String => ByteString =
    (content: String) => ByteString.fromString(content)

  private implicit val byteStringToString: ByteString => String =
    (content: ByteString) => content.utf8String
}
