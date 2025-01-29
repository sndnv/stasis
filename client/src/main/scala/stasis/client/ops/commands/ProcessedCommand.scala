package stasis.client.ops.commands

import stasis.core.commands.proto.Command

final case class ProcessedCommand(command: Command, isProcessed: Boolean)
