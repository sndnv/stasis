package stasis.client_android.lib.collection.rules.exceptions

class RuleMatchingFailure(message: String, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String) : this(message, cause = null)
}
