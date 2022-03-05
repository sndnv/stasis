package stasis.client_android.activities.helpers

import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.format.Formatter
import android.text.style.CharacterStyle
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import stasis.client_android.BuildConfig
import stasis.client_android.R
import stasis.client_android.activities.receivers.LogoutReceiver
import stasis.client_android.lib.api.clients.exceptions.AccessDeniedFailure
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.lib.security.exceptions.ExplicitLogout
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.math.BigInteger
import java.nio.file.Path
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

object Common {
    fun UUID.toMinimizedString(): String =
        this.toString().split("-").last()

    fun Duration.toFields(): Pair<Int, ChronoUnit> {
        return if (seconds > 0 && seconds % 60 == 0L) {
            val minutes = (seconds / 60).toInt()
            if (minutes % 60 == 0) {
                val hours = minutes / 60
                if (hours % 24 == 0) {
                    val days = (hours / 24)
                    days to ChronoUnit.DAYS
                } else {
                    hours to ChronoUnit.HOURS
                }
            } else {
                minutes to ChronoUnit.MINUTES
            }
        } else {
            seconds.toInt() to ChronoUnit.SECONDS
        }
    }

    fun Duration.asString(context: Context): String {
        val (amount, unit) = this.toFields()
        return context.getString(
            R.string.duration,
            amount.toString(),
            unit.asQuantityString(amount, context)
        )
    }

    fun Long.asString(): String {
        return NumberFormat.getInstance().format(this)
    }

    fun BigInteger.asSizeString(context: Context): String {
        return this.toLong().asSizeString(context)
    }

    fun Long.asSizeString(context: Context): String {
        return Formatter.formatFileSize(context, this)
    }

    fun Operation.Type?.asString(context: Context): String {
        return when (this) {
            Operation.Type.Backup -> context.getString(R.string.operation_type_backup)
            Operation.Type.Recovery -> context.getString(R.string.operation_type_recovery)
            Operation.Type.Expiration -> context.getString(R.string.operation_type_expiration)
            Operation.Type.Validation -> context.getString(R.string.operation_type_validation)
            Operation.Type.KeyRotation -> context.getString(R.string.operation_type_key_rotation)
            Operation.Type.GarbageCollection -> context.getString(R.string.operation_type_garbage_collection)
            else -> context.getString(R.string.operation_type_garbage_unknown)
        }
    }

    fun DatasetDefinition.Retention.asString(context: Context): String {
        val duration = this.duration.asString(context)

        return when (val policy = this.policy) {
            is DatasetDefinition.Retention.Policy.AtMost -> context.resources.getQuantityString(
                R.plurals.dataset_definition_retention_at_most,
                policy.versions,
                duration,
                policy.versions.toString()
            )
            is DatasetDefinition.Retention.Policy.LatestOnly -> context.getString(
                R.string.dataset_definition_retention_latest_only,
                duration
            )
            is DatasetDefinition.Retention.Policy.All -> context.getString(
                R.string.dataset_definition_retention_all,
                duration
            )
        }
    }

    fun String.toPolicyTypeString(context: Context): String = when (this) {
        "at-most" -> context.getString(R.string.policy_at_most)
        "latest-only" -> context.getString(R.string.policy_latest_only)
        "all" -> context.getString(R.string.policy_all)
        else -> throw IllegalArgumentException("Unexpected DatasetDefinition Retention Policy provided: [$this]")
    }

    fun String.fromPolicyTypeString(context: Context): String = when (this) {
        context.getString(R.string.policy_at_most) -> "at-most"
        context.getString(R.string.policy_latest_only) -> "latest-only"
        context.getString(R.string.policy_all) -> "all"
        else -> throw IllegalArgumentException("Unexpected DatasetDefinition Retention Policy provided: [$this]")
    }

    fun String.toAssignmentTypeString(context: Context): String = when (this) {
        "backup" -> context.getString(R.string.assignment_type_backup)
        "expiration" -> context.getString(R.string.assignment_type_expiration)
        "validation" -> context.getString(R.string.assignment_type_validation)
        "key-rotation" -> context.getString(R.string.assignment_type_key_rotation)
        else -> throw IllegalArgumentException("Unexpected assignment type provided: [$this]")
    }

    fun String.fromAssignmentTypeString(context: Context): String = when (this) {
        context.getString(R.string.assignment_type_backup) -> "backup"
        context.getString(R.string.assignment_type_expiration) -> "expiration"
        context.getString(R.string.assignment_type_validation) -> "validation"
        context.getString(R.string.assignment_type_key_rotation) -> "key-rotation"
        else -> throw IllegalArgumentException("Unexpected assignment type provided: [$this]")
    }

    fun OperationScheduleAssignment.toAssignmentTypeString(context: Context): String = when (this) {
        is OperationScheduleAssignment.Backup -> "backup"
        is OperationScheduleAssignment.Expiration -> "expiration"
        is OperationScheduleAssignment.Validation -> "validation"
        is OperationScheduleAssignment.KeyRotation -> "key-rotation"
    }.toAssignmentTypeString(context)

    fun String.toOperationStageString(context: Context): String = when (this) {
        "specification" -> context.getString(R.string.operation_stage_specification)
        "examination" -> context.getString(R.string.operation_stage_examination)
        "collection" -> context.getString(R.string.operation_stage_collection)
        "processing" -> context.getString(R.string.operation_stage_processing)
        "metadata" -> context.getString(R.string.operation_stage_metadata)
        "metadata-applied" -> context.getString(R.string.operation_stage_metadata_applied)
        else -> context.getString(R.string.operation_stage_unknown, this)
    }

    fun String.toOperationStepString(context: Context): String = when (this) {
        "processing" -> context.getString(R.string.operation_step_processing)
        "collection" -> context.getString(R.string.operation_step_collection)
        "push" -> context.getString(R.string.operation_step_push)
        else -> context.getString(R.string.operation_step_unknown, this)
    }

    fun EntityMetadata.size(): Long? =
        (this as? EntityMetadata.File)?.size

    fun EntityMetadata.checksum(): BigInteger? =
        (this as? EntityMetadata.File)?.checksum

    fun EntityMetadata.crates(): Int? =
        (this as? EntityMetadata.File)?.crates?.size

    fun String.asChangedString(context: Context): String =
        when (this) {
            "content" -> context.getString(R.string.dataset_metadata_field_content_summary_changed_content)
            "metadata" -> context.getString(R.string.dataset_metadata_field_content_summary_changed_metadata)
            else -> throw IllegalArgumentException("Unexpected value provided: [$this]")
        }

    fun Any?.asString(context: Context): String =
        when (this) {
            null -> context.getString(R.string.empty_value)
            is Int -> this.toString()
            is Long -> this.toString()
            is BigInteger -> this.toString()
            is Path -> this.toString()
            is Boolean -> if (this) context.getString(R.string.yes) else context.getString(R.string.no)
            is String -> this
            else -> throw IllegalArgumentException("Unexpected value provided: [$this]")
        }

    fun ChronoUnit.asString(context: Context): String = when {
        this == ChronoUnit.DAYS -> context.getString(R.string.duration_plural_days)
        this == ChronoUnit.HOURS -> context.getString(R.string.duration_plural_hours)
        this == ChronoUnit.MINUTES -> context.getString(R.string.duration_plural_minutes)
        this == ChronoUnit.SECONDS -> context.getString(R.string.duration_plural_seconds)
        else -> throw IllegalArgumentException("Unexpected ChronoUnit provided: [${this.name}]")
    }

    fun ChronoUnit.asQuantityString(amount: Int, context: Context): String = when {
        this == ChronoUnit.DAYS -> context.resources.getQuantityString(
            R.plurals.duration_days,
            amount
        )
        this == ChronoUnit.HOURS -> context.resources.getQuantityString(
            R.plurals.duration_hours,
            amount
        )
        this == ChronoUnit.MINUTES -> context.resources.getQuantityString(
            R.plurals.duration_minutes,
            amount
        )
        this == ChronoUnit.SECONDS -> context.resources.getQuantityString(
            R.plurals.duration_seconds,
            amount
        )
        else -> throw IllegalArgumentException("Unexpected ChronoUnit provided: [${this.name}]")
    }

    fun String.asChronoUnit(context: Context): ChronoUnit = when {
        this == context.getString(R.string.duration_plural_days) -> ChronoUnit.DAYS
        this == context.getString(R.string.duration_plural_hours) -> ChronoUnit.HOURS
        this == context.getString(R.string.duration_plural_minutes) -> ChronoUnit.MINUTES
        this == context.getString(R.string.duration_plural_seconds) -> ChronoUnit.SECONDS
        else -> throw IllegalArgumentException("Unexpected ChronoUnit string provided: [$this]")
    }

    fun Set<DayOfWeek>.asString(withFirstDayOfWeek: DayOfWeek): String {
        val partitioned = sorted().partition { it >= withFirstDayOfWeek }
        return (partitioned.first + partitioned.second).joinToString(", ") {
            it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    data class StyledString(val placeholder: String, val content: String, val style: CharacterStyle)

    fun String.renderAsSpannable(vararg strings: StyledString): SpannableString {
        val (prepared, entries) = strings.fold(
            initial = this to emptyList<Pair<Int, StyledString>>()
        ) { (collected, indexes), value ->
            val index = collected.indexOf(value.placeholder)
            val replaced = collected.replace(value.placeholder, value.content)

            (replaced to indexes + (index to value))
        }

        val string = SpannableString(prepared)

        entries.forEach { (index, value) ->
            if (index >= 0) {
                string.setSpan(
                    value.style,
                    index,
                    index + value.content.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return string
    }

    fun <T> Try<T>.getOrRenderFailure(withContext: Context): T? = when (this) {
        is Success -> this.value
        is Failure -> {
            val e = this.exception

            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }

            val message = when (e) {
                is ResourceMissingFailure -> withContext.getString(R.string.api_resource_missing_failure)
                is AccessDeniedFailure -> withContext.getString(R.string.api_access_denied_failure)
                is ExplicitLogout -> null // do nothing
                else -> withContext.getString(R.string.api_other_failure, e.message)
            }

            message?.let { Toast.makeText(withContext, it, Toast.LENGTH_SHORT).show() }

            if (e is AccessDeniedFailure) {
                LocalBroadcastManager.getInstance(withContext).sendBroadcast(
                    Intent().apply { action = LogoutReceiver.Action }
                )
            }

            null
        }
    }
}
