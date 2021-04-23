package stasis.client_android.lib.collection.rules

import stasis.client_android.lib.collection.rules.exceptions.RuleParsingFailure
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

data class Rule(
    val operation: Operation,
    val directory: String,
    val pattern: String,
    val comment: String?,
    val original: Original
) {
    sealed class Operation {
        object Include : Operation()
        object Exclude : Operation()
    }

    data class Original(
        val line: String,
        val lineNumber: Int
    )

    companion object {

        private val rule: Regex = Regex("""^([+-])(.+?)(?:\s+(?:#|//)(.*))?$""")
        private val directoryPattern: Regex = Regex("""^(.+?)\s(?=(?:"[^"]*"|[^"])*$)(.+)$""")

        fun extractOperation(operation: String, lineNumber: Int): Try<Operation> =
            when (operation) {
                "+" -> Success(Operation.Include)
                "-" -> Success(Operation.Exclude)
                else -> Failure(RuleParsingFailure("Invalid rule operation provided on line [$lineNumber]: [$operation]"))
            }

        fun extractDirectoryPattern(rule: String, lineNumber: Int): Try<Pair<String, String>> {
            val trimmed = rule.trim()

            return when (val result = directoryPattern.find(trimmed)) {
                null -> Failure(
                    RuleParsingFailure("Invalid rule directory and/or pattern provided on line [$lineNumber]: [$trimmed]")
                )
                else -> {
                    val (directory, pattern) = result.destructured

                    Success(Pair(trimQuotedString(directory), trimQuotedString(pattern)))
                }
            }
        }

        fun trimQuotedString(string: String): String =
            string.trim().replace("^\"|\"$".toRegex(), "").trim()

        operator fun invoke(line: String, lineNumber: Int): Try<Rule> {
            return when (val result = rule.find(line)) {
                null -> Failure(RuleParsingFailure("Invalid rule definition found on line [$lineNumber]: [$line]"))
                else -> {
                    val (operation, rule, comment) = result.destructured

                    extractOperation(operation, lineNumber).flatMap { op ->
                        extractDirectoryPattern(rule, lineNumber).map { (directory, pattern) ->
                            Rule(
                                operation = op,
                                directory = directory,
                                pattern = pattern,
                                comment = comment.trim().let { if (it.isEmpty()) null else it },
                                original = Original(line, lineNumber)
                            )
                        }
                    }
                }
            }
        }
    }
}
