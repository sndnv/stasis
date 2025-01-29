package stasis.client_android.mocks

import stasis.client_android.lib.ops.commands.CommandProcessor
import stasis.client_android.lib.utils.Try
import stasis.core.commands.proto.Command

class MockCommandProcessor(private val commands: List<Command> = emptyList()) : CommandProcessor {
    override suspend fun all(): Try<List<Command>> = Try.Success(commands)

    override suspend fun latest(): Try<List<Command>> = Try.Success(emptyList())

    override suspend fun stop() {
        // do nothing
    }
}