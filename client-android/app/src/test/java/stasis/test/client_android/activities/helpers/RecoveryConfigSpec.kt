package stasis.test.client_android.activities.helpers

import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.activities.helpers.RecoveryConfig
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.scheduling.OperationExecutor
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class RecoveryConfigSpec {
    @Test
    fun validateItself() {
        assertThat(
            config.validate(),
            equalTo(RecoveryConfig.ValidationResult.MissingDefinition)
        )

        assertThat(
            config.copy(definition = UUID.randomUUID()).validate(),
            equalTo(RecoveryConfig.ValidationResult.Valid)
        )

        assertThat(
            config.copy(
                definition = UUID.randomUUID(),
                recoverySource = RecoveryConfig.RecoverySource.Entry(entry = null)
            ).validate(),
            equalTo(RecoveryConfig.ValidationResult.MissingEntry)
        )

        assertThat(
            config.copy(
                definition = UUID.randomUUID(),
                recoverySource = RecoveryConfig.RecoverySource.Entry(entry = UUID.randomUUID())
            ).validate(),
            equalTo(RecoveryConfig.ValidationResult.Valid)
        )

        assertThat(
            config.copy(
                definition = UUID.randomUUID(),
                recoverySource = RecoveryConfig.RecoverySource.Until(instant = Instant.now())
            ).validate(),
            equalTo(RecoveryConfig.ValidationResult.Valid)
        )
    }

    @Test
    fun provideRecoveryDefinition() {
        val definition = UUID.randomUUID()

        assertThat(config.copy(definition = definition).recoveryDefinition, equalTo(definition))
    }

    @Test
    fun failToProvideMissingRecoveryDefinition() {
        try {
            config.recoveryDefinition
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected empty definition encountered"))
        }
    }

    @Test
    fun provideRecoveryEntry() {
        val entry = UUID.randomUUID()

        assertThat(
            config.copy(recoverySource = RecoveryConfig.RecoverySource.Entry(entry = entry)).recoveryEntry,
            equalTo(entry)
        )
    }

    @Test
    fun failToProvideMissingRecoveryEntry() {
        try {
            config.copy(recoverySource = RecoveryConfig.RecoverySource.Entry(entry = null)).recoveryEntry
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected empty source entry encountered"))
        }
    }

    @Test
    fun failToProvideUnexpectedRecoveryEntry() {
        try {
            config.recoveryEntry
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected recovery source encountered: [Latest]"))
        }
    }

    @Test
    fun provideRecoveryDestination() {
        assertThat(
            config.recoveryDestination,
            equalTo(null)
        )

        assertThat(
            config.copy(destination = "test").recoveryDestination,
            equalTo(Recovery.Destination(path = "test", keepStructure = true))
        )

        assertThat(
            config.copy(destination = "test", discardPaths = true).recoveryDestination,
            equalTo(Recovery.Destination(path = "test", keepStructure = false))
        )
    }

    @Test
    fun provideRecoveryPathQuery() {
        assertThat(
            config.recoveryPathQuery,
            equalTo(null)
        )

        assertThat(
            config.copy(pathQuery = "test").recoveryPathQuery.toString(),
            equalTo(Recovery.PathQuery(query = "test").toString())
        )
    }

    @Test
    fun startRecoveryWithLatestEntry() {
        val recoveryExecuted = AtomicBoolean(false)

        val executor = object : TestExecutor() {
            override suspend fun startRecoveryWithDefinition(
                definition: DatasetDefinitionId,
                until: Instant?,
                query: Recovery.PathQuery?,
                destination: Recovery.Destination?,
                f: (Throwable?) -> Unit
            ): OperationId {
                require(until == null)
                recoveryExecuted.set(true)
                return UUID.randomUUID()
            }
        }

        runBlocking {
            config.copy(definition = UUID.randomUUID()).startRecovery(withExecutor = executor) { }

            assertThat(recoveryExecuted.get(), equalTo(true))
        }
    }

    @Test
    fun startRecoveryWithSpecificEntry() {
        val recoveryExecuted = AtomicBoolean(false)

        val executor = object : TestExecutor() {
            override suspend fun startRecoveryWithEntry(
                entry: DatasetEntryId,
                query: Recovery.PathQuery?,
                destination: Recovery.Destination?,
                f: (Throwable?) -> Unit
            ): OperationId {
                recoveryExecuted.set(true)
                return UUID.randomUUID()
            }
        }

        runBlocking {
            config.copy(
                definition = UUID.randomUUID(),
                recoverySource = RecoveryConfig.RecoverySource.Entry(entry = UUID.randomUUID())
            ).startRecovery(withExecutor = executor) { }

            assertThat(recoveryExecuted.get(), equalTo(true))
        }
    }

    @Test
    fun startRecoveryUntilTimestamp() {
        val recoveryExecuted = AtomicBoolean(false)

        val executor = object : TestExecutor() {
            override suspend fun startRecoveryWithDefinition(
                definition: DatasetDefinitionId,
                until: Instant?,
                query: Recovery.PathQuery?,
                destination: Recovery.Destination?,
                f: (Throwable?) -> Unit
            ): OperationId {
                require(until != null)
                recoveryExecuted.set(true)
                return UUID.randomUUID()
            }
        }

        runBlocking {
            config.copy(
                definition = UUID.randomUUID(),
                recoverySource = RecoveryConfig.RecoverySource.Until(instant = Instant.now())
            ).startRecovery(withExecutor = executor) { }

            assertThat(recoveryExecuted.get(), equalTo(true))
        }
    }

    private val config = RecoveryConfig(
        definition = null,
        recoverySource = RecoveryConfig.RecoverySource.Latest,
        pathQuery = null,
        destination = null,
        discardPaths = false
    )

    private abstract class TestExecutor : OperationExecutor {
        override suspend fun startRecoveryWithDefinition(
            definition: DatasetDefinitionId,
            until: Instant?,
            query: Recovery.PathQuery?,
            destination: Recovery.Destination?,
            f: (Throwable?) -> Unit
        ): OperationId = UUID.randomUUID()

        override suspend fun startRecoveryWithEntry(
            entry: DatasetEntryId,
            query: Recovery.PathQuery?,
            destination: Recovery.Destination?,
            f: (Throwable?) -> Unit
        ): OperationId = UUID.randomUUID()

        override suspend fun startBackupWithEntities(
            definition: DatasetDefinitionId,
            entities: List<Path>,
            f: (Throwable?) -> Unit
        ): OperationId = UUID.randomUUID()

        override suspend fun startBackupWithRules(
            definition: DatasetDefinitionId,
            rules: List<Rule>,
            f: (Throwable?) -> Unit
        ): OperationId = UUID.randomUUID()

        override suspend fun startExpiration(f: (Throwable?) -> Unit): OperationId = UUID.randomUUID()
        override suspend fun startValidation(f: (Throwable?) -> Unit): OperationId = UUID.randomUUID()
        override suspend fun startKeyRotation(f: (Throwable?) -> Unit): OperationId = UUID.randomUUID()
        override suspend fun stop(operation: OperationId) = Unit
        override suspend fun active(): Map<OperationId, Operation.Type> = emptyMap()
        override suspend fun completed(): Map<OperationId, Operation.Type> = emptyMap()
        override suspend fun find(operation: OperationId): Operation.Type? = null
    }
}
