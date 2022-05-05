package stasis.client_android.lib.ops.recovery.stages.internal

import okio.Source
import stasis.client_android.lib.utils.ConcatSource
import java.nio.file.Path

object MergedCrates {
    fun List<Pair<Path, Source>>.merged(): Source {
        val sorted = this.sortedBy { it.first }.map { it.second }

        return when {
            sorted.isNotEmpty() -> ConcatSource(sorted)
            else -> throw IllegalArgumentException("Expected at least one crate but none were found")
        }
    }
}