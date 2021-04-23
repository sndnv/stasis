package stasis.test.client_android.lib.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right

class EitherSpec : WordSpec({
    "Either" should {
        "support mapping" {
            Right<String, String>(value = "test").map { it.toUpperCase() } shouldBe (Right("TEST"))
            Left<String, String>(value = "test").map { it.toUpperCase() } shouldBe (Left("test"))
        }

        "support flat-mapping" {
            Right<String, String>(value = "test").flatMap { Right(it.toUpperCase()) } shouldBe (Right("TEST"))
            Right<String, String>(value = "test").flatMap { Left<String, String>(it.toUpperCase()) } shouldBe (Left("TEST"))
            Left<String, String>(value = "test").flatMap { Right(it.toUpperCase()) } shouldBe (Left("test"))
            Left<String, String>(value = "test").flatMap { Left<String, String>(it.toUpperCase()) } shouldBe (Left("test"))
        }

        "support folding" {
            Right<String, String>(value = "tESt").fold(fl = { it.toLowerCase() }, fr = { it.toUpperCase() }) shouldBe ("TEST")
            Left<String, String>(value = "tESt").fold(fl = { it.toLowerCase() }, fr = { it.toUpperCase() }) shouldBe ("test")
        }

        "support retrieving 'left' on a 'Left'" {
            Left<String, String>(value = "test").isLeft shouldBe(true)
            Left<String, String>(value = "test").isRight shouldBe(false)
            Left<String, String>(value = "test").left shouldBe ("test")
        }

        "fail when retrieving 'right' on a 'Left'" {
            shouldThrow<NoSuchElementException> {
                Left<String, String>(value = "test").right
            }
        }

        "support retrieving 'right' on a 'Right'" {
            Right<String, String>(value = "test").isLeft shouldBe(false)
            Right<String, String>(value = "test").isRight shouldBe(true)
            Right<String, String>(value = "test").right shouldBe ("test")
        }

        "fail when retrieving 'left' on a 'Right'" {
            shouldThrow<NoSuchElementException> {
                Right<String, String>(value = "test").left
            }
        }
    }
})
