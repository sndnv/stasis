package stasis.client_android.lib.ops.scheduling

import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.schedules.ScheduleId
import java.nio.file.Path

sealed class OperationScheduleAssignment {
    abstract val schedule: ScheduleId

    data class Backup(
        override val schedule: ScheduleId,
        val definition: DatasetDefinitionId,
        val entities: List<Path>
    ) : OperationScheduleAssignment()

    data class Expiration(
        override val schedule: ScheduleId
    ) : OperationScheduleAssignment()

    data class Validation(
        override val schedule: ScheduleId
    ) : OperationScheduleAssignment()

    data class KeyRotation(
        override val schedule: ScheduleId
    ) : OperationScheduleAssignment()
}
