package stasis.test.client_android.lib.collection.rules

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.collection.rules.Rule

class RuleSpec : WordSpec({
    "A Rule" should {
        "support rendering as string" {
            val rule1 = Rule(id = 1, operation = Rule.Operation.Include, directory = "/work", pattern = "?")
            val rule2 = Rule(id = 2, operation = Rule.Operation.Exclude, directory = "/work", pattern = "[a-z]")
            val rule3 = Rule(id = 3, operation = Rule.Operation.Include, directory = "/work", pattern = "{0|1}")
            val rule4 = Rule(id = 4, operation = Rule.Operation.Exclude, directory = "/work/root", pattern = "**/q")

            rule1.asString() shouldBe ("+ /work ?")
            rule2.asString() shouldBe ("- /work [a-z]")
            rule3.asString() shouldBe ("+ /work {0|1}")
            rule4.asString() shouldBe ("- /work/root **/q")
        }
    }
})
