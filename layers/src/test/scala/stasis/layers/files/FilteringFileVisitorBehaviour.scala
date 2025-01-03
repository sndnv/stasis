package stasis.layers.files

import java.nio.file.AccessDeniedException
import java.nio.file.Files

import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.Logger
import stasis.layers.FileSystemHelpers
import stasis.layers.FileSystemHelpers.FileSystemSetup
import stasis.layers.UnitSpec

trait FilteringFileVisitorBehaviour { _: UnitSpec with FileSystemHelpers with AsyncMockitoSugar =>
  def visitor(setup: FileSystemSetup): Unit = {
    it should "collect files and handle failures based on a provided path matcher" in {
      val (fs, _) = createMockFileSystem(setup)

      val dir = fs.getPath("/test")
      val file1 = fs.getPath("/test/file-1")
      val file2 = fs.getPath("/test/file-2")
      val file3 = fs.getPath("/file-3")
      val file4 = fs.getPath("/file-4")

      val visitor = FilteringFileVisitor(matcher = fs.getPathMatcher(s"glob:{/test,/test/*}"))

      visitor.matched should be(empty)
      visitor.failed should be(empty)

      visitor.preVisitDirectory(dir, null)

      visitor.matched should be(Seq(dir))
      visitor.failed should be(empty)

      visitor.visitFile(file1, null)
      visitor.visitFile(file2, null)
      visitor.visitFile(file3, null)

      visitor.visitFileFailed(file4, new AccessDeniedException(file4.toString))

      visitor.postVisitDirectory(dir, null)

      visitor.matched should be(Seq(dir, file1, file2))
      visitor.failed should be(Seq(file4 -> "AccessDeniedException - /file-4"))
    }

    it should "support walking the file tree from a starting path" in {
      val file1 = "other-file-1"
      val file2 = "other-file-2"
      val file3 = "other-file-3"
      val file4 = "other-file-4"

      val (fs, _) = createMockFileSystem(setup)

      val path = fs.getPath("/test")
      val subdir1 = path.resolve("subdir1")
      val subdir2 = subdir1.resolve("subdir2")

      Files.createDirectories(path)
      Files.createDirectories(subdir1)
      Files.createDirectories(subdir2)

      Files.createFile(path.resolve(file1))
      Files.createFile(path.resolve(file2))
      Files.createFile(subdir1.resolve(file3))
      Files.createFile(subdir2.resolve(file4))

      val FilteringFileVisitor.Result(collected, failed) =
        FilteringFileVisitor(matcher = fs.getPathMatcher(s"glob:/test/{*,**/*}")).walk(start = path)

      collected.map(_.getFileName.toString).sorted should be(
        Seq(file1, file2, file3, file4, "subdir1", "subdir2").sorted
      )

      failed should be(empty)
    }

    it should "support providing only successfully collected files" in {
      val (fs, _) = createMockFileSystem(setup)

      val logger = mock[Logger]
      val captor = ArgCaptor[String]

      val successful = Seq(fs.getPath("/test"))
      val failed = Seq(fs.getPath("/test/1") -> "Test failure #1", fs.getPath("/test/2") -> "Test failure #2")

      val result = FilteringFileVisitor.Result(matched = successful, failed = failed)

      result.successful(logger) should be(successful)

      verify(logger, times(2)).debug(
        eqTo("Visiting entity [{}] failed with [{}]"),
        captor.capture,
        captor.capture
      )

      captor.values.take(2) match {
        case path :: failure :: Nil =>
          path should be("/test/1")
          failure should be("Test failure #1")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      captor.values.takeRight(2) match {
        case path :: failure :: Nil =>
          path should be("/test/2")
          failure should be("Test failure #2")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      succeed
    }
  }
}
