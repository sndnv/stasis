package stasis.client_android.activities.fragments.rules

data class RuleSuggestion(
    val include: Boolean,
    val description: String,
    val directory: String,
    val pattern: String
)