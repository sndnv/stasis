package stasis.test.specs.unit.client.service

import stasis.client.service.ApplicationArguments
import stasis.test.specs.unit.AsyncUnitSpec

class ApplicationArgumentsSpec extends AsyncUnitSpec {
  "ApplicationArguments" should "support parsing arguments (service)" in {
    val expectedMode = ApplicationArguments.Mode.Service

    for {
      defaultServiceMode <- ApplicationArguments(
        applicationName = "test-application",
        args = Array.empty
      )
      explicitServiceMode <- ApplicationArguments(
        applicationName = "test-application",
        args = Array("service")
      )
    } yield {
      defaultServiceMode should be(ApplicationArguments(mode = expectedMode))
      explicitServiceMode should be(ApplicationArguments(mode = expectedMode))
    }
  }

  they should "support parsing arguments (bootstrap)" in {
    val expectedMode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = "test-password".toCharArray
    )

    ApplicationArguments(
      applicationName = "test-application",
      args = Array(
        "bootstrap",
        "--server",
        expectedMode.serverBootstrapUrl,
        "--code",
        expectedMode.bootstrapCode,
        "--accept-self-signed",
        "--user-password",
        expectedMode.userPassword.mkString
      )
    )
      .map {
        case ApplicationArguments(mode: ApplicationArguments.Mode.Bootstrap) =>
          mode.serverBootstrapUrl should be(expectedMode.serverBootstrapUrl)
          mode.bootstrapCode should be(expectedMode.bootstrapCode)
          mode.acceptSelfSignedCertificates should be(expectedMode.acceptSelfSignedCertificates)
          mode.userPassword.mkString should be(expectedMode.userPassword.mkString)

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
  }

  they should "fail if invalid arguments are provided" in {
    ApplicationArguments(
      applicationName = "test-application",
      args = Array("invalid")
    ).failed
      .map { e =>
        e.getMessage should be("Invalid arguments provided")
      }
  }

  they should "validate bootstrap mode arguments" in {
    val mode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = Array.emptyCharArray
    )

    noException should be thrownBy {
      mode.validate()
    }
  }

  they should "fail if non-HTTPS bootstrap server URL is provided" in {
    val mode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "http://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userPassword = Array.emptyCharArray
    )

    an[IllegalArgumentException] should be thrownBy {
      mode.validate()
    }
  }

  they should "fail if an empty bootstrap code is provided" in {
    val mode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "",
      acceptSelfSignedCertificates = true,
      userPassword = Array.emptyCharArray
    )

    an[IllegalArgumentException] should be thrownBy {
      mode.validate()
    }
  }
}
