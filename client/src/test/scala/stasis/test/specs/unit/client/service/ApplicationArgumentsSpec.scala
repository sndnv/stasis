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
      userPasswordConfirm = "test-password".toCharArray,
      recreateFiles = true
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
        expectedMode.userPassword.mkString,
        "--recreate-files"
      )
    ).map {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Bootstrap) =>
        mode.serverBootstrapUrl should be(expectedMode.serverBootstrapUrl)
        mode.bootstrapCode should be(expectedMode.bootstrapCode)
        mode.acceptSelfSignedCertificates should be(expectedMode.acceptSelfSignedCertificates)
        mode.userName should be(expectedMode.userName)
        mode.userPassword.mkString should be(expectedMode.userPassword.mkString)
        mode.recreateFiles should be(true)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "support parsing arguments (maintenance regenerate-api-certificate)" in {
    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "regenerate-api-certificate")
    ).await match {
      case ApplicationArguments(ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate) =>
        succeed

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  they should "support parsing arguments (maintenance credentials)" in {
    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "credentials", "reset")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ResetUserCredentials) =>
        mode.currentUserPassword should be(empty)
        mode.newUserPassword should be(empty)
        mode.newUserSalt should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "credentials", "reset", "--current-user-password", "test-password")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ResetUserCredentials) =>
        new String(mode.currentUserPassword) should be("test-password")
        mode.newUserPassword should be(empty)
        mode.newUserSalt should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "credentials", "reset", "--new-user-password", "test-password")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ResetUserCredentials) =>
        mode.currentUserPassword should be(empty)
        new String(mode.newUserPassword) should be("test-password")
        mode.newUserSalt should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "credentials", "reset", "--new-user-salt", "test-salt")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ResetUserCredentials) =>
        mode.currentUserPassword should be(empty)
        mode.newUserPassword should be(empty)
        mode.newUserSalt should be("test-salt")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array(
        "maintenance",
        "credentials",
        "reset",
        "--current-user-password",
        "current-password",
        "--new-user-password",
        "new-password",
        "--new-user-salt",
        "test-salt"
      )
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ResetUserCredentials) =>
        new String(mode.currentUserPassword) should be("current-password")
        new String(mode.newUserPassword) should be("new-password")
        mode.newUserSalt should be("test-salt")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "credentials", "other")
    ).failed
      .map { e =>
        e.getMessage should be("Invalid arguments provided")
      }
  }

  they should "support parsing arguments (maintenance secret)" in {
    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "secret", "push")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret) =>
        mode.currentUserName should be(empty)
        mode.currentUserPassword should be(empty)
        mode.remotePassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "secret", "pull")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PullDeviceSecret) =>
        mode.currentUserName should be(empty)
        mode.currentUserPassword should be(empty)
        mode.remotePassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "secret", "re-encrypt")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret) =>
        mode.currentUserName should be(empty)
        mode.currentUserPassword should be(empty)
        mode.oldUserPassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "secret", "push", "--current-user-name", "test-user")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret) =>
        mode.currentUserName should be("test-user")
        mode.currentUserPassword should be(empty)
        mode.remotePassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "secret", "push", "--current-user-password", "test-password")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret) =>
        mode.currentUserName should be(empty)
        new String(mode.currentUserPassword) should be("test-password")
        mode.remotePassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args =
        Array("maintenance", "secret", "push", "--current-user-name", "test-user", "--current-user-password", "test-password")
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret) =>
        mode.currentUserName should be("test-user")
        new String(mode.currentUserPassword) should be("test-password")
        mode.remotePassword should be(empty)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array(
        "maintenance",
        "secret",
        "push",
        "--current-user-name",
        "test-user",
        "--current-user-password",
        "test-password",
        "--remote-password",
        "other-password"
      )
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PushDeviceSecret) =>
        mode.currentUserName should be("test-user")
        new String(mode.currentUserPassword) should be("test-password")
        mode.remotePassword.map(new String(_)) should be(Some("other-password"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array(
        "maintenance",
        "secret",
        "pull",
        "--current-user-name",
        "test-user",
        "--current-user-password",
        "test-password",
        "--remote-password",
        "other-password"
      )
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.PullDeviceSecret) =>
        mode.currentUserName should be("test-user")
        new String(mode.currentUserPassword) should be("test-password")
        mode.remotePassword.map(new String(_)) should be(Some("other-password"))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array(
        "maintenance",
        "secret",
        "re-encrypt",
        "--current-user-name",
        "test-user",
        "--current-user-password",
        "test-password",
        "--old-user-password",
        "other-password"
      )
    ).await match {
      case ApplicationArguments(mode: ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret) =>
        mode.currentUserName should be("test-user")
        new String(mode.currentUserPassword) should be("test-password")
        new String(mode.oldUserPassword) should be("other-password")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }

    ApplicationArguments(
      applicationName = "test-application",
      args = Array("maintenance", "secret", "other")
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
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
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
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
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
      userPasswordConfirm = Array.emptyCharArray,
      recreateFiles = false
    )

    an[IllegalArgumentException] should be thrownBy {
      mode.validate()
    }
  }

  they should "validate maintenance mode arguments" in {
    noException should be thrownBy { ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate.validate() }
    noException should be thrownBy { ApplicationArguments.Mode.Maintenance.PushDeviceSecret.empty.validate() }
    noException should be thrownBy { ApplicationArguments.Mode.Maintenance.PullDeviceSecret.empty.validate() }
    noException should be thrownBy { ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret.empty.validate() }
    noException should be thrownBy { ApplicationArguments.Mode.Maintenance.ResetUserCredentials.empty.validate() }
    an[IllegalArgumentException] should be thrownBy { ApplicationArguments.Mode.Maintenance.Empty.validate() }
  }
}
