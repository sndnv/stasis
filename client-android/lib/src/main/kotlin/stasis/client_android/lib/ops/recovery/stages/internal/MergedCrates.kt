package stasis.client_android.lib.ops.recovery.stages.internal

import okio.Source
import stasis.client_android.lib.utils.ConcatSource

object MergedCrates {
    fun List<Triple<Int, String, suspend () -> Source>>.merged(onPartProcessed: () -> Unit): Source {
        val sorted = this.sortedBy { it.first }.map { it.third }

        return when {
            sorted.isNotEmpty() -> ConcatSource(sorted, onSourceConsumed = onPartProcessed)
            else -> throw IllegalArgumentException("Expected at least one crate but none were found")
        }
    }
}
