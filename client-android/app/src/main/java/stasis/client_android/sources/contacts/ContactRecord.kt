package stasis.client_android.sources.contacts

data class ContactRecord(
    val id: String,
    val displayName: String,
    val starred: Boolean,
    val data: List<ContactDataRow>
)
