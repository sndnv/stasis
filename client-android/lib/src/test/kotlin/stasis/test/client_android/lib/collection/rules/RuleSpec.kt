package stasis.test.client_android.lib.collection.rules

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

class RuleSpec : WordSpec({
    "A Rule" should {
        "support trimming quoted strings" {
            val expectedString = "test 42"

            val strings = listOf(
                expectedString,
                " $expectedString",
                "$expectedString ",
                " $expectedString ",
                "    $expectedString    ",
                "\"$expectedString\"",
                "\" $expectedString\"",
                "\"$expectedString\" ",
                " \"$expectedString\" ",
                "   \"$expectedString\"   ",
                " \"$expectedString\" ",
                " \" $expectedString\" ",
                " \"$expectedString \" ",
                " \" $expectedString \" ",
                " \"   $expectedString   \" "
            )

            strings.forEach { string ->
                val actualString = Rule.trimQuotedString(string)
                actualString shouldBe (expectedString)
            }
        }

        "support extracting operations from a raw operation string" {
            Rule.extractOperation(operation = "+", lineNumber = 0) shouldBe (Success(Rule.Operation.Include))

            Rule.extractOperation(operation = "-", lineNumber = 0) shouldBe (Success(Rule.Operation.Exclude))

            when (val result = Rule.extractOperation(operation = "?", lineNumber = 0)) {
                is Success -> fail("Unexpected result received: [$result]")
                is Failure -> result.exception.message shouldBe ("Invalid rule operation provided on line [0]: [?]")
            }
        }

        "support extracting directories and patterns from a raw directory/pattern string" {
            val validDirectoryPatternRules = mapOf(
                "/home/user   *               " to Pair("/home/user", "*"),
                "/home/user   .*/             " to Pair("/home/user", ".*/"),
                "/home/user   .sbt/*.sbt      " to Pair("/home/user", ".sbt/*.sbt"),
                "/home/user   *.{conf,config} " to Pair("/home/user", "*.{conf,config}"),
                "/home/user   .*rc            " to Pair("/home/user", ".*rc"),
                "/etc         *               " to Pair("/etc", "*"),
                " /etc        *               " to Pair("/etc", "*"),
                "   /etc      *               " to Pair("/etc", "*"),
                "/            *               " to Pair("/", "*"),
                "\"/home/user/some directory\" *.conf" to Pair("/home/user/some directory", "*.conf"),
                "\"/directory name with whitespace\" pattern with whitespace" to Pair(
                    "/directory name with whitespace",
                    "pattern with whitespace"
                )
            )

            val invalidDirectoryPatternsRules = listOf(
                "/directory-without-pattern",
                "/",
                ".*",
                ""
            )

            validDirectoryPatternRules.forEach {
                val rule = it.key
                val (directory, pattern) = it.value

                val actualDirectoryPattern = Rule.extractDirectoryPattern(rule, lineNumber = 0)
                actualDirectoryPattern shouldBe (Success(Pair(directory, pattern)))
            }

            invalidDirectoryPatternsRules.forEach { rule ->
                when (val result = Rule.extractDirectoryPattern(rule, lineNumber = 0)) {
                    is Success -> fail("Unexpected result received: [$result]")
                    is Failure -> result.exception.message shouldBe ("Invalid rule directory and/or pattern provided on line [0]: [$rule]")
                }
            }
        }

        "support extracting rules from a raw rule string" {
            val validRules = mapOf(
                "+ /home/user   *         #  include all user files " to Rule(
                    operation = Rule.Operation.Include,
                    directory = "/home/user",
                    pattern = "*",
                    comment = "include all user files",
                    original = Rule.Original(line = "", lineNumber = 0)
                ),
                "- /home/user   .ssh      #  exclude ssh directory  " to Rule(
                    operation = Rule.Operation.Exclude,
                    directory = "/home/user",
                    pattern = ".ssh",
                    comment = "exclude ssh directory",
                    original = Rule.Original(line = "", lineNumber = 0)
                ),
                "+ /etc         *.conf    // include all conf files " to Rule(
                    operation = Rule.Operation.Include,
                    directory = "/etc",
                    pattern = "*.conf",
                    comment = "include all conf files",
                    original = Rule.Original(line = "", lineNumber = 0)
                ),
                "- /etc/test    *cache*   // exclude all cache files" to Rule(
                    operation = Rule.Operation.Exclude,
                    directory = "/etc/test",
                    pattern = "*cache*",
                    comment = "exclude all cache files",
                    original = Rule.Original(line = "", lineNumber = 0)
                ),
                "+   \"/var/log/some service\" *" to Rule(
                    operation = Rule.Operation.Include,
                    directory = "/var/log/some service",
                    pattern = "*",
                    comment = null,
                    original = Rule.Original(line = "", lineNumber = 0)
                )
            )

            val invalidRules = listOf(
                "/home/user * # include all user files",
                "? /home/user * # include all user files",
                "/home/user *",
                "/home/user",
                ""
            )

            validRules.forEach { (ruleString, expectedRule) ->
                Rule(
                    line = ruleString,
                    lineNumber = 0
                ) shouldBe (Success(expectedRule.copy(original = Rule.Original(line = ruleString, lineNumber = 0))))
            }

            invalidRules.forEach { ruleString ->
                when (val result = Rule(line = ruleString, lineNumber = 0)) {
                    is Success -> fail("Unexpected result received: [$result]")
                    is Failure -> result.exception.message shouldBe ("Invalid rule definition found on line [0]: [$ruleString]")
                }
            }
        }
    }
})
