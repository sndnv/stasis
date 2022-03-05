package stasis.client_android.lib.collection.rules

data class Rule(
    val id: Long,
    val operation: Operation,
    val directory: String,
    val pattern: String
) {
    fun asString(): String {
        val operationAsString = when(operation) {
            is Operation.Include -> "+"
            is Operation.Exclude -> "-"
        }

        return "$operationAsString $directory $pattern"
    }

    sealed class Operation {
        object Include : Operation()
        object Exclude : Operation()
    }
}
