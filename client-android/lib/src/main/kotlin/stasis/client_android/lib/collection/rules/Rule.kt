package stasis.client_android.lib.collection.rules

import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

data class Rule(
    val id: Long,
    val operation: Operation,
    val directory: String,
    val pattern: String,
    val definition: DatasetDefinitionId?
) {
    fun asString(): String {
        val operationAsString = when (operation) {
            is Operation.Include -> "+"
            is Operation.Exclude -> "-"
        }

        val definitionAsString = when (definition) {
            null -> ""
            else -> "($definition)"
        }

        return "$operationAsString $directory $pattern $definitionAsString".trim()
    }

    sealed class Operation {
        object Include : Operation()
        object Exclude : Operation()
    }
}
