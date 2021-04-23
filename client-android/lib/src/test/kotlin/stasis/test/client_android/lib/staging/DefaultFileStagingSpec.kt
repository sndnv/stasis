package stasis.test.client_android.lib.staging

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.test.client_android.lib.ResourceHelpers.content
import stasis.test.client_android.lib.ResourceHelpers.write
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions

class DefaultFileStagingSpec : WordSpec({
    "DefaultFileStaging" should {
        "create temporary staging files" {
            val staging = DefaultFileStaging(
                storeDirectory = null,
                prefix = "staging-test-",
                suffix = ".tmp"
            )

            val file = staging.temporary()

            file.toFile().deleteOnExit()
            val filename = file.fileName.toString()
            val attributes = Files.readAttributes(file, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val permissions = PosixFilePermissions.toString(attributes.permissions())

            filename.startsWith("staging-test-") shouldBe (true)
            filename.endsWith(".tmp") shouldBe (true)
            Files.size(file) shouldBe (0)
            permissions shouldBe ("rw-------")
        }

        "create temporary staging files in a dedicated directory" {
            val directory = Paths.get("/tmp/${java.util.UUID.randomUUID()}")
            Files.createDirectories(directory)
            directory.toFile().deleteOnExit()

            val staging = DefaultFileStaging(
                storeDirectory = directory,
                prefix = "staging-test-",
                suffix = ".tmp"
            )

            val file = staging.temporary()
            val attributes = Files.readAttributes(file, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val permissions = PosixFilePermissions.toString(attributes.permissions())

            file.toFile().deleteOnExit()
            file.toString().startsWith("$directory/staging-test-") shouldBe (true)
            file.toString().endsWith(".tmp") shouldBe (true)
            Files.size(file) shouldBe (0)
            permissions shouldBe ("rw-------")
        }

        "discard temporary staging files" {
            val staging = DefaultFileStaging(
                storeDirectory = null,
                prefix = "staging-test-",
                suffix = ".tmp"
            )

            val file = staging.temporary()
            val fileCreated = Files.exists(file)
            fileCreated shouldBe (true)

            staging.discard(file)
            val fileDeleted = !Files.exists(file)
            fileDeleted shouldBe (true)
        }

        "destage incoming files" {
            val staging = DefaultFileStaging(
                storeDirectory = null,
                prefix = "staging-test-",
                suffix = ".tmp"
            )

            val sourceFileContent = "source-content"
            val targetFileContent = "target-content"

            val source = staging.temporary()
            val sourceCreated = Files.exists(source)
            sourceCreated shouldBe (true)

            source.write(content = sourceFileContent)
            val target = staging.temporary()
            target.toFile().deleteOnExit()

            val targetCreated = Files.exists(target)
            targetCreated shouldBe (true)

            target.write(content = targetFileContent)

            staging.destage(from = source, to = target)
            val sourceMoved = !Files.exists(source)
            sourceMoved shouldBe (true)

            val targetContent = target.content()
            targetContent shouldBe (sourceFileContent)
        }
    }
})
