package stasis.test.client_android.lib.ops

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.ops.Operation

class OperationSpec : WordSpec({
    "An Operation Type" should {
        "support converting to/from string" {
            Operation.Type.Backup.toString() shouldBe ("Backup")
            Operation.Type.Recovery.toString() shouldBe ("Recovery")
            Operation.Type.Expiration.toString() shouldBe ("Expiration")
            Operation.Type.Validation.toString() shouldBe ("Validation")
            Operation.Type.KeyRotation.toString() shouldBe ("KeyRotation")
            Operation.Type.GarbageCollection.toString() shouldBe ("GarbageCollection")

            Operation.Type.fromString("Backup") shouldBe (Operation.Type.Backup)
            Operation.Type.fromString("Recovery") shouldBe (Operation.Type.Recovery)
            Operation.Type.fromString("Expiration") shouldBe (Operation.Type.Expiration)
            Operation.Type.fromString("Validation") shouldBe (Operation.Type.Validation)
            Operation.Type.fromString("KeyRotation") shouldBe (Operation.Type.KeyRotation)
            Operation.Type.fromString("GarbageCollection") shouldBe (Operation.Type.GarbageCollection)

            val e = shouldThrow<IllegalArgumentException> {
                Operation.Type.fromString("other")
            }

            e.message shouldBe ("Unexpected operation type provided: [other]")
        }
    }
})
