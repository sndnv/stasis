package stasis.test.client_android.lib.collection.rules

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.collection.rules.Rule
import java.util.UUID

class RuleSpec : WordSpec({
    "A Rule" should {
        "support rendering as string" {
            val rule1 = Rule(
                id = 1,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "?",
                definition = null
            )

            val rule2 = Rule(
                id = 2,
                operation = Rule.Operation.Exclude,
                directory = "/work",
                pattern = "[a-z]",
                definition = UUID.randomUUID()
            )

            val rule3 = Rule(
                id = 3,
                operation = Rule.Operation.Include,
                directory = "/work",
                pattern = "{0|1}",
                definition = null
            )

            val rule4 = Rule(
                id = 4,
                operation = Rule.Operation.Exclude,
                directory = "/work/root",
                pattern = "**/q",
                definition = null
            )

            rule1.asString() shouldBe ("+ /work ?")
            rule2.asString() shouldBe ("- /work [a-z] (${rule2.definition})")
            rule3.asString() shouldBe ("+ /work {0|1}")
            rule4.asString() shouldBe ("- /work/root **/q")
        }
    }
})
