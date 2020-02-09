package stasis.test.specs.unit.client.service

import java.io.FileNotFoundException
import java.nio.file.Files

import akka.util.ByteString
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class ApplicationDirectorySpec extends AsyncUnitSpec with ResourceHelpers {
  "An ApplicationDirectory" should "find files in any configuration location (config)" in {
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

  it should "find files in any configuration location (current)" in {
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

  it should "find files in any configuration location (user)" in {
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

  it should "find required files in any configuration location" in {
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

  it should "fail if a required file is not found" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    directory
      .requireFile(file = targetFile)
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover {
        case e: FileNotFoundException =>
          e.getMessage should startWith(s"File [$targetFile] not found")
      }
  }

  it should "handle missing configuration locations" in {
    val directory = createApplicationDirectory(init = _ => ())

    directory
      .requireFile(file = targetFile)
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover {
        case e: FileNotFoundException =>
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

  it should "push data to files (permanent / file does not exist)" in {
    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        Files.createDirectories(path)
        Files.createFile(path.resolve(targetFile))
      }
    )

    directory
      .pushFile[String](file = targetFile, content = targetFileContent, isTransient = false)
      .map { path =>
        Some(path) should be(directory.config.map(_.resolve(targetFile).toAbsolutePath))
        Files.readString(path) should be(targetFileContent)
      }
  }

  it should "push data to files (permanent / file exists)" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    directory
      .pushFile[String](file = targetFile, content = targetFileContent, isTransient = false)
      .map { path =>
        Some(path) should be(directory.config.map(_.resolve(targetFile).toAbsolutePath))
        Files.readString(path) should be(targetFileContent)
      }
  }

  it should "push data to files (transient)" in {
    val directory = createApplicationDirectory(init = dir => Files.createDirectories(dir.config.get))

    directory
      .pushFile[String](file = targetFile, content = targetFileContent, isTransient = true)
      .map { path =>
        val fileInConfig = directory.config.map(_.resolve(targetFile).toAbsolutePath)

        Some(path) should be(fileInConfig)
        Files.readString(path) should be(targetFileContent)
      }
  }

  it should "fail to push data if no configuration location is available" in {
    val directory = createApplicationDirectory(init = _ => ())

    directory
      .pushFile[String](file = targetFile, content = targetFileContent, isTransient = true)
      .map { result =>
        fail(s"Unexpected result received [$result]")
      }
      .recover {
        case e: IllegalStateException =>
          e.getMessage should be(s"File [$targetFile] could not be created; no suitable directory available")
      }
  }

  private val targetFile = "test-file"
  private val targetFileContent = "test-content-line-01\ntest-content-line-02"

  private implicit val stringToByteString: String => ByteString =
    (content: String) => ByteString.fromString(content)

  private implicit val byteStringToString: ByteString => String =
    (content: ByteString) => content.utf8String
}
