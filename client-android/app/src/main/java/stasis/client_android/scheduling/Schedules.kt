package stasis.client_android.scheduling

import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.ops.scheduling.ActiveSchedule

data class Schedules(
    val public: List<Schedule>,
    val local: List<Schedule>,
    val configured: List<ActiveSchedule>
) {
    companion object {
        fun emtpy(): Schedules = Schedules(
            public = emptyList(),
            local = emptyList(),
            configured = emptyList(),
        )
    }
}
