package stasis.test.client_android.lib

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import okio.buffer
import okio.sink
import okio.source
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.core.CrateId
import java.math.BigInteger
import java.nio.file.FileSystem
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

object ResourceHelpers {
    fun String.asPath(): Path =
        Paths.get(this)

    fun String.asTestResource(): Path {
        return Paths.get(ResourceHelpers.javaClass.getResource(this)!!.path)
    }

    fun Path.write(content: String) {
        require(!Files.isDirectory(this)) { "Expected [$this] to be a file" }

        this.sink().buffer().use { sink ->
            sink.write(content.toByteArray())
        }
    }

    fun Path.content(): String {
        require(!Files.isDirectory(this)) { "Expected [$this] to be a file" }

        return this.source().buffer().use { source ->
            source.readString(Charsets.UTF_8)
        }
    }

    suspend fun Path.clear() {
        require(Files.isDirectory(this)) { "Expected [$this] to be a directory" }

        val resourcePathAsString = this.toAbsolutePath().toString()
        val build = "build"
        val testResources = "resources"

        val pathIsUnderBuild = resourcePathAsString.contains(build)
        val pathIsUnderTestResources = resourcePathAsString.contains(testResources)
        require(pathIsUnderBuild && pathIsUnderTestResources) { "Expected [$this] to be under $build/$testResources" }

        val pathEndsInBuild =
            resourcePathAsString.endsWith(build) || resourcePathAsString.endsWith("$build/")
        val pathEndsInTestResources =
            resourcePathAsString.endsWith(testResources) || resourcePathAsString.endsWith("$testResources/")
        require(!pathEndsInBuild && !pathEndsInTestResources) { "Expected [$this] to be a child of $build/$testResources" }

        Files
            .walk(this, *emptyArray<FileVisitOption>())
            .filter { path -> !Files.isHidden(path) && path != this }
            .sorted()
            .toList()
            .map {
                if (Files.isDirectory(it)) {
                    it.clear()
                    Files.deleteIfExists(it)
                } else {
                    Files.deleteIfExists(it)
                }
            }
    }

    fun Path.files(): List<Path> {
        require(Files.isDirectory(this)) { "Expected [$this] to be a directory" }

        return Files.walk(this).filter { Files.isRegularFile(it) }.toList()
    }

    suspend fun Path.extractFileMetadata(
        withChecksum: BigInteger,
        withCrate: CrateId
    ): EntityMetadata.File {
        val baseMetadata = Metadata.extractBaseEntityMetadata(this)

        require(!baseMetadata.isDirectory) { "Expected [$this] to be a file" }

        return EntityMetadata.File(
            path = baseMetadata.path.toAbsolutePath().toString(),
            size = baseMetadata.attributes.size(),
            link = baseMetadata.link?.toAbsolutePath().toString(),
            isHidden = baseMetadata.isHidden,
            created = baseMetadata.created,
            updated = baseMetadata.updated,
            owner = baseMetadata.owner,
            group = baseMetadata.group,
            permissions = baseMetadata.permissions,
            checksum = withChecksum,
            crates = mapOf(baseMetadata.path.toAbsolutePath().toString() to withCrate),
            compression = "none"
        )
    }

    suspend fun Path.extractDirectoryMetadata(): EntityMetadata.Directory {
        val baseMetadata = Metadata.extractBaseEntityMetadata(this)
        require(baseMetadata.isDirectory) { "Expected [$this] to be a directory" }

        return EntityMetadata.Directory(
            path = baseMetadata.path.toAbsolutePath().toAbsolutePath().toString(),
            link = baseMetadata.link?.toAbsolutePath()?.toAbsolutePath()?.toString(),
            isHidden = baseMetadata.isHidden,
            created = baseMetadata.created,
            updated = baseMetadata.updated,
            owner = baseMetadata.owner,
            group = baseMetadata.group,
            permissions = baseMetadata.permissions
        )
    }

    suspend fun Path.extractFileMetadata(checksum: Checksum): EntityMetadata.File {
        val calculatedChecksum = checksum.calculate(this)
        val baseMetadata = Metadata.extractBaseEntityMetadata(this)

        require(!baseMetadata.isDirectory) { "Expected [$this] to be a file" }

        return EntityMetadata.File(
            path = baseMetadata.path.toAbsolutePath().toString(),
            size = baseMetadata.attributes.size(),
            link = baseMetadata.link?.toAbsolutePath()?.toString(),
            isHidden = baseMetadata.isHidden,
            created = baseMetadata.created,
            updated = baseMetadata.updated,
            owner = baseMetadata.owner,
            group = baseMetadata.group,
            permissions = baseMetadata.permissions,
            checksum = calculatedChecksum,
            crates = mapOf(baseMetadata.path.toAbsolutePath().toString() to UUID.randomUUID()),
            compression = "gzip"
        )
    }

    fun EntityMetadata.File.withFilesystem(filesystem: FileSystem): EntityMetadata.File =
        this.copy(path = filesystem.getPath(this.path).toAbsolutePath().toString())

    fun EntityMetadata.File.withRootAt(path: String): EntityMetadata.File {
        val originalPath = this.path

        val updatedPath = when (val remainingPath = originalPath.split(path).lastOrNull()) {
            null -> "$path$originalPath"
            else -> "$path$remainingPath"
        }

        return this.copy(path = updatedPath)
    }

    fun EntityMetadata.Directory.withFilesystem(filesystem: FileSystem): EntityMetadata.Directory =
        this.copy(path = filesystem.getPath(this.path).toAbsolutePath().toString())

    fun EntityMetadata.Directory.withRootAt(path: String): EntityMetadata.Directory {
        val originalPath = this.path

        val updatedPath = if (originalPath.endsWith(path)) {
            path
        } else {
            when (val remainingPath = originalPath.split(path).lastOrNull()) {
                null -> "$path$originalPath"
                else -> "$path$remainingPath"
            }
        }

        return this.copy(path = updatedPath)
    }

    fun createMockFileSystem(setup: FileSystemSetup): Pair<FileSystem, FileSystemObjects> {
        val filesystem = Jimfs.newFileSystem(Configuration.unix())

        val chars: Set<Char> = setup.chars
            .map { char ->
                if (setup.caseSensitive) char else char.lowercaseChar()
            }
            .filterNot { setup.disallowedChars.contains(it) }
            .toSet()

        val rootDirectories = chars.map { char -> "root-dir-$char" }

        val nestedParentDirs = (0..setup.nestedParentDirs).map { i -> "root/parent-$i" }

        val nestedDirectories = chars.flatMap { char ->
            nestedParentDirs.map { parent ->
                "$parent/child-dir-$char"
            }
        }

        val files = chars
            .map { it.toString() }
            .filterNot { setup.disallowedFileNames.contains(it) }
            .take(setup.maxFilesPerDir)

        files.forEach { file ->
            Files.createFile(filesystem.getPath(file))
        }

        rootDirectories.forEach { directory ->
            val parent = Files.createDirectory(filesystem.getPath(directory))
            files.forEach { file ->
                Files.createFile(parent.resolve(filesystem.getPath(file)))
            }
        }

        nestedDirectories.forEach { directory ->
            val parent = Files.createDirectories(filesystem.getPath(directory))
            files.forEach { file ->
                Files.createFile(parent.resolve(filesystem.getPath(file)))
            }
        }

        return Pair(
            filesystem,
            FileSystemObjects(
                filesPerDir = files.size,
                rootDirs = rootDirectories.size,
                nestedParentDirs = nestedParentDirs.size,
                nestedChildDirsPerParent = chars.size,
                nestedDirs = nestedDirectories.size
            )
        )
    }

    data class FileSystemSetup(
        val config: Configuration,
        val chars: List<Char>,
        val disallowedChars: List<Char>,
        val disallowedFileNames: List<String>,
        val maxFilesPerDir: Int,
        val nestedParentDirs: Int,
        val caseSensitive: Boolean
    ) {
        companion object {
            fun empty(): FileSystemSetup = Unix.copy(maxFilesPerDir = 0, nestedParentDirs = 0)

            val DefaultChars: List<Char> = (Byte.MIN_VALUE..Byte.MAX_VALUE).map { it.toChar() }
            val AlphaNumericChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

            val Unix: FileSystemSetup = FileSystemSetup(
                config = Configuration.unix(),
                chars = DefaultChars,
                disallowedChars = listOf('\u0000', '/', '\n', '\r'),
                disallowedFileNames = listOf(".", ".."),
                maxFilesPerDir = Int.MAX_VALUE,
                nestedParentDirs = 4,
                caseSensitive = true
            )
        }
    }

    data class FileSystemObjects(
        val filesPerDir: Int,
        val rootDirs: Int,
        val nestedParentDirs: Int,
        val nestedChildDirsPerParent: Int,
        val nestedDirs: Int
    ) {
        val total: Int = filesPerDir + rootDirs * filesPerDir + nestedDirs * filesPerDir
    }

    data class RuleExpectation(
        val excluded: Int,
        val included: Int,
        val root: Int
    )
}
