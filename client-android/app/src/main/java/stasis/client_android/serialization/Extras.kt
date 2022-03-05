package stasis.client_android.serialization

import android.content.Intent
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.persistence.Converters.Companion.toActiveSchedule
import stasis.client_android.persistence.Converters.Companion.toJson

object Extras {
    fun Intent.putActiveSchedule(extra: String, activeSchedule: ActiveSchedule): Intent {
        return putExtra(extra, activeSchedule.toJson())
    }

    fun Intent.requireActiveSchedule(extra: String): ActiveSchedule {
        val activeSchedule = getStringExtra(extra)?.toActiveSchedule()
        require(activeSchedule != null) { "Expected active schedule [$extra] but none was provided" }

        return activeSchedule
    }

    fun Intent.putActiveScheduleId(extra: String, activeScheduleId: Long): Intent {
        require(activeScheduleId != MissingActiveScheduleId) { "Invalid active schedule ID [$extra] provided" }

        return putExtra(extra, activeScheduleId)
    }

    fun Intent.requireActiveScheduleId(extra: String): Long {
        val activeScheduleId = getLongExtra(extra, MissingActiveScheduleId)
        require(activeScheduleId != MissingActiveScheduleId) { "Expected active schedule ID [$extra] but none was provided" }

        return activeScheduleId
    }


    private const val MissingActiveScheduleId: Long = Long.MIN_VALUE
}
