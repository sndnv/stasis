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
      userName = "test-user",
      userPassword = "test-password".toCharArray,
      userPasswordConfirm = "test-password".toCharArray
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
        "--user-name",
        expectedMode.userName,
        "--user-password",
        expectedMode.userPassword.mkString
      )
    ).map {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Bootstrap) =>
        mode.serverBootstrapUrl should be(expectedMode.serverBootstrapUrl)
        mode.bootstrapCode should be(expectedMode.bootstrapCode)
        mode.acceptSelfSignedCertificates should be(expectedMode.acceptSelfSignedCertificates)
        mode.userName should be(expectedMode.userName)
        mode.userPassword.mkString should be(expectedMode.userPassword.mkString)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "support parsing arguments (maintenance)" in {
    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--regenerate-api-certificate")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
        mode.regenerateApiCertificate should be(true)
        mode.deviceSecretOperation should be(None)
        mode.userName should be(empty)
        mode.userPassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--secret", "push")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
        mode.regenerateApiCertificate should be(false)
        mode.deviceSecretOperation should be(Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push))
        mode.userName should be(empty)
        mode.userPassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--secret", "pull")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
        mode.regenerateApiCertificate should be(false)
        mode.deviceSecretOperation should be(Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Pull))
        mode.userName should be(empty)
        mode.userPassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--secret", "push", "--user-name", "test-user")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
        mode.regenerateApiCertificate should be(false)
        mode.deviceSecretOperation should be(Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push))
        mode.userName should be("test-user")
        mode.userPassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--secret", "push", "--user-password", "test-password")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
        mode.regenerateApiCertificate should be(false)
        mode.deviceSecretOperation should be(Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push))
        mode.userName should be(empty)
        new String(mode.userPassword) should be("test-password")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--secret", "push", "--user-name", "test-user", "--user-password", "test-password")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance) =>
        mode.regenerateApiCertificate should be(false)
        mode.deviceSecretOperation should be(Some(ApplicationArguments.Mode.Maintenance.DeviceSecretOperation.Push))
        mode.userName should be("test-user")
        new String(mode.userPassword) should be("test-password")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "--secret", "other")
    ).failed
      .map { e =>
        e.getMessage should be("Invalid arguments provided")
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
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
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
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
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
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      userPasswordConfirm = Array.emptyCharArray
    )

    an[IllegalArgumentException] should be thrownBy {
      mode.validate()
    }
  }

  they should "validate maintenance mode arguments" in {
    val mode = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = true,
      deviceSecretOperation = None,
      userName = "",
      userPassword = Array.emptyCharArray
    )

    noException should be thrownBy {
      mode.validate()
    }
  }

  they should "fail if invalid maintenance mode arguments are provided" in {
    val mode = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = true,
      deviceSecretOperation = None,
      userName = "",
      userPassword = Array.emptyCharArray
    )

    an[IllegalArgumentException] should be thrownBy {
      mode.copy(regenerateApiCertificate = false).validate()
    }
  }
}
