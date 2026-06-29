package stasis.test.client_android.activities.fragments.backup

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import stasis.client_android.activities.fragments.backup.EntryGrouping

class EntryGroupingSpec {
    @Test
    fun groupFilesystemFirstThenSchemesSorted() {
        val entities = listOf(
            "contacts:/source-1",
            "/storage/emulated/0/test.pdf",
            "calendar:/uid-1",
            "/storage/emulated/0/test.jpg",
            "calendar:/uid-2"
        )

        val grouped = EntryGrouping.groupEntitiesByScheme(entities)

        assertThat(grouped.map { it.first }, equalTo(listOf(null, "calendar", "contacts")))
        assertThat(
            grouped[0].second,
            equalTo(listOf("/storage/emulated/0/test.pdf", "/storage/emulated/0/test.jpg"))
        )
        assertThat(grouped[1].second, equalTo(listOf("calendar:/uid-1", "calendar:/uid-2")))
        assertThat(grouped[2].second, equalTo(listOf("contacts:/source-1")))
    }

    @Test
    fun groupWithoutFilesystemEntries() {
        val grouped = EntryGrouping.groupEntitiesByScheme(listOf("calendar:/uid-1", "contacts:/source-1"))

        assertThat(grouped.map { it.first }, equalTo(listOf("calendar", "contacts")))
    }

    @Test
    fun groupEmptyEntities() {
        assertThat(EntryGrouping.groupEntitiesByScheme(emptyList()), equalTo(emptyList()))
    }
}
