package stasis.test.client_android.lib.telemetry

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.telemetry.ApplicationInformation
import java.time.Instant

class ApplicationInformationSpec : WordSpec({
    "A none ApplicationInformation" should {
        "provide no information" {
            ApplicationInformation.none().name shouldBe ("none")
            ApplicationInformation.none().version shouldBe ("none")
            ApplicationInformation.none().buildTime shouldBe (0L)
            ApplicationInformation.none().asString() shouldBe ("none;none;0")
        }
    }

    "A valid ApplicationInformation" should {
        "provide information" {
            val now = Instant.now().toEpochMilli()

            val app = object : ApplicationInformation {
                override val name: String = "test-name"
                override val version: String = "test-version"
                override val buildTime: Long = now
            }

            app.name shouldBe ("test-name")
            app.version shouldBe ("test-version")
            app.buildTime shouldBe (now)
            app.asString() shouldBe ("test-name;test-version;$now")
        }
    }
})
