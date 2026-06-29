package stasis.test.client_android.lib.ops.recovery

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Source
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.FileSystemSetup
import stasis.test.client_android.lib.ResourceHelpers.asPath
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import java.nio.file.FileSystem
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class RecoveryEntityKindSpec : WordSpec({
    fun createProviders(filesystem: FileSystem): Providers {
        val stagingDirectory = Files.createDirectories(filesystem.getPath("/staging"))

        return Providers(
            checksum = Checksum.Companion.MD5,
            staging = DefaultFileStaging(storeDirectory = stagingDirectory, prefix = "staged-", suffix = ".tmp"),
            compression = MockCompression(),
            decryptor = MockEncryption(),
            clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
            track = MockRecoveryTracker(),
            analytics = AnalyticsCollector.NoOp,
            kinds = listOf(RecoveryEntityKind.Filesystem)
        )
    }

    fun libraryEntity(scheme: String): TargetEntity =
        TargetEntity(
            ref = EntityRef.Library(scheme = scheme, path = "/album/img.heic"),
            destination = TargetEntity.Destination.Default,
            existingMetadata = Fixtures.Metadata.FileOneMetadata,
            currentMetadata = null
        )

    val filesystemEntity = TargetEntity(
        ref = EntityRef.Filesystem(Fixtures.Metadata.FileOneMetadata.path.asPath()),
        destination = TargetEntity.Destination.Default,
        existingMetadata = Fixtures.Metadata.FileOneMetadata,
        currentMetadata = null
    )

    class MockLibraryKind : RecoveryEntityKind.Library {
        val prepared: AtomicBoolean = AtomicBoolean(false)

        override val scheme: String = "photos"

        override fun collector(
            targetMetadata: DatasetMetadata,
            keep: (String, FilesystemMetadata.EntityState) -> Boolean,
            destination: TargetEntity.Destination,
            providers: Providers
        ): RecoveryCollector = throw NotImplementedError()

        override fun prepare(entity: TargetEntity, ref: EntityRef.Library, providers: Providers) {
            prepared.set(true)
        }

        override suspend fun write(entity: TargetEntity, ref: EntityRef.Library, content: Source, providers: Providers) =
            Unit

        override suspend fun applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: Providers) =
            Unit
    }

    "A filesystem RecoveryEntityKind" should {
        "prepare and write entity content to its destination" {
            val targetDirectory = Files.createTempDirectory("recovery-kind-target")
            targetDirectory.toFile().deleteOnExit()

            val targetPath = targetDirectory.resolve("nested").resolve("file.txt")

            val entity = TargetEntity(
                ref = EntityRef.Filesystem(targetPath),
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileOneMetadata.copy(path = targetPath.toString()),
                currentMetadata = null
            )

            val providers = Providers(
                checksum = Checksum.Companion.MD5,
                staging = DefaultFileStaging(storeDirectory = null, prefix = "staged-", suffix = ".tmp"),
                compression = MockCompression(),
                decryptor = MockEncryption(),
                clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
                track = MockRecoveryTracker(),
                analytics = AnalyticsCollector.NoOp,
                kinds = listOf(RecoveryEntityKind.Filesystem)
            )

            val ref = EntityRef.Filesystem(targetPath)

            val filesystemKind = RecoveryEntityKind.Filesystem
            filesystemKind.prepare(entity = entity, ref = ref, providers = providers)
            Files.isDirectory(targetPath.parent) shouldBe (true)

            filesystemKind.write(
                entity = entity,
                ref = ref,
                content = Buffer().writeUtf8("hello"),
                providers = providers
            )

            String(Files.readAllBytes(targetPath)) shouldBe ("hello")
        }
    }

    "A RecoveryEntityKind dispatcher" should {
        "route prepare to a matching library kind" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)
            val kind = MockLibraryKind()

            RecoveryEntityKind.prepare(
                kinds = listOf(kind),
                entity = libraryEntity("photos"),
                providers = createProviders(filesystem)
            )

            kind.prepared.get() shouldBe (true)
        }

        "skip prepare when no kind is registered for a library scheme" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            shouldNotThrowAny {
                RecoveryEntityKind.prepare(
                    kinds = listOf(RecoveryEntityKind.Filesystem),
                    entity = libraryEntity("unknown"),
                    providers = createProviders(filesystem)
                )
            }
        }

        "fail to write when no kind is registered for a filesystem ref" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val e = shouldThrow<IllegalArgumentException> {
                RecoveryEntityKind.write(
                    kinds = emptyList(),
                    entity = filesystemEntity,
                    content = Buffer().writeUtf8("x"),
                    providers = createProviders(filesystem)
                )
            }

            e.message shouldBe ("No filesystem recovery kind was registered")
        }

        "route writes to a matching library kind" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            shouldNotThrowAny {
                RecoveryEntityKind.write(
                    kinds = listOf(MockLibraryKind()),
                    entity = libraryEntity("photos"),
                    content = Buffer().writeUtf8("x"),
                    providers = createProviders(filesystem)
                )
            }
        }

        "fail to write when no kind is registered for a library scheme" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val e = shouldThrow<IllegalArgumentException> {
                RecoveryEntityKind.write(
                    kinds = listOf(RecoveryEntityKind.Filesystem),
                    entity = libraryEntity("unknown"),
                    content = Buffer().writeUtf8("x"),
                    providers = createProviders(filesystem)
                )
            }

            e.message shouldBe ("No recovery kind was registered for scheme [unknown]")
        }

        "fail to apply metadata when no kind is registered for a filesystem ref" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val e = shouldThrow<IllegalArgumentException> {
                RecoveryEntityKind.applyMetadata(
                    kinds = emptyList(),
                    entity = filesystemEntity,
                    providers = createProviders(filesystem)
                )
            }

            e.message shouldBe ("No filesystem recovery kind was registered")
        }

        "route apply metadata to a matching library kind" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            shouldNotThrowAny {
                RecoveryEntityKind.applyMetadata(
                    kinds = listOf(MockLibraryKind()),
                    entity = libraryEntity("photos"),
                    providers = createProviders(filesystem)
                )
            }
        }

        "fail to apply metadata when no kind is registered for a library scheme" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val e = shouldThrow<IllegalArgumentException> {
                RecoveryEntityKind.applyMetadata(
                    kinds = listOf(RecoveryEntityKind.Filesystem),
                    entity = libraryEntity("unknown"),
                    providers = createProviders(filesystem)
                )
            }

            e.message shouldBe ("No recovery kind was registered for scheme [unknown]")
        }
    }
})
