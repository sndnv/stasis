package stasis.client_android.activities.fragments.backup

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import stasis.client_android.R
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.utils.StringPaths.splitParentAndName
import stasis.client_android.sources.calendar.CalendarSource
import stasis.client_android.sources.contacts.ContactsSource
import java.nio.file.FileSystem

object EntryDisplay {
    private val gson = Gson()
    private val attributesType = object : TypeToken<Map<String, String>>() {}.type

    fun decodeAttributes(metadata: EntityMetadata?): Map<String, String> {
        val library = metadata as? EntityMetadata.Library ?: return emptyMap()
        val json = library.attributes.utf8()
        if (json.isBlank()) return emptyMap()

        return runCatching { gson.fromJson<Map<String, String>>(json, attributesType) }.getOrNull().orEmpty()
    }

    fun displayName(entity: String, metadata: EntityMetadata?, filesystem: FileSystem): String {
        decodeAttributes(metadata)["name"]?.takeIf { it.isNotBlank() }?.let { return it }

        return entity.splitParentAndName(filesystem).second
    }

    @DrawableRes
    fun kindIcon(scheme: String?, metadata: EntityMetadata?): Int = when {
        scheme == CalendarSource.Scheme -> R.drawable.ic_sources_calendar
        scheme == ContactsSource.Scheme -> R.drawable.ic_sources_contacts
        scheme != null -> R.drawable.ic_sources
        metadata is EntityMetadata.Directory -> R.drawable.ic_tree_directory
        else -> R.drawable.ic_tree_file
    }

    fun kindLabel(context: Context, scheme: String?, metadata: EntityMetadata?): String = when {
        scheme == CalendarSource.Scheme -> context.getString(R.string.dataset_metadata_kind_calendar)
        scheme == ContactsSource.Scheme -> context.getString(R.string.dataset_metadata_kind_contact)
        scheme != null -> scheme
        metadata is EntityMetadata.Directory -> context.getString(R.string.dataset_metadata_kind_directory)
        else -> context.getString(R.string.dataset_metadata_kind_file)
    }

    fun secondary(
        context: Context,
        entity: String,
        scheme: String?,
        metadata: EntityMetadata?,
        filesystem: FileSystem
    ): String = when (scheme) {
        CalendarSource.Scheme -> decodeAttributes(metadata)["calendar"]
            ?.let { context.getString(R.string.dataset_metadata_kind_calendar_named, it) }
            ?: context.getString(R.string.dataset_metadata_kind_calendar)

        ContactsSource.Scheme -> context.getString(R.string.dataset_metadata_kind_contact)
        null -> entity.splitParentAndName(filesystem).first
        else -> scheme
    }

    @StringRes
    fun stateLabel(state: FilesystemMetadata.EntityState): Int = when (state) {
        is FilesystemMetadata.EntityState.New -> R.string.dataset_metadata_state_new
        is FilesystemMetadata.EntityState.Updated -> R.string.dataset_metadata_state_updated
        is FilesystemMetadata.EntityState.Existing -> R.string.dataset_metadata_state_existing
    }

    @ColorRes
    fun stateColor(state: FilesystemMetadata.EntityState): Int = when (state) {
        is FilesystemMetadata.EntityState.New -> R.color.launcher_tertiary_2
        is FilesystemMetadata.EntityState.Updated -> R.color.primary
        is FilesystemMetadata.EntityState.Existing -> R.color.secondary_light
    }
}
