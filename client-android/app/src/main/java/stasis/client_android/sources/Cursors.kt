package stasis.client_android.sources

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri

object Cursors {
    internal inline fun <T> Cursor.collect(block: (Cursor) -> T): List<T> {
        val results = mutableListOf<T>()
        while (moveToNext()) {
            results.add(block(this))
        }
        return results
    }

    internal inline fun <T> ContentResolver.queryList(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        block: (Cursor) -> List<T>
    ): List<T> =
        query(uri, projection, selection, selectionArgs, sortOrder)?.use(block) ?: emptyList()

    internal inline fun <T> ContentResolver.queryFirst(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        row: (Cursor) -> T?
    ): T? =
        query(uri, projection, selection, selectionArgs, sortOrder)?.use { if (it.moveToFirst()) row(it) else null }
}
