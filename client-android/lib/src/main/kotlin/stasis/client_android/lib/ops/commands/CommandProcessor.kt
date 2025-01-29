package stasis.client_android.lib.ops.commands

import stasis.client_android.lib.utils.Try
import stasis.core.commands.proto.Command

interface CommandProcessor {
    suspend fun all(): Try<List<Command>>
    suspend fun latest(): Try<List<Command>>
    suspend fun stop()

    interface Handlers {
        suspend fun persistLastProcessedCommand(sequenceId: Long)
        suspend fun retrieveLastProcessedCommand(): Long
        suspend fun executeCommands(commands: List<Command>): Long?
    }
}
