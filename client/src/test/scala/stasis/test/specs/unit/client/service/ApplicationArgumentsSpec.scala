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

  they should "expand bootstrap mode arguments from provided env vars" in {
    val mode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      recreateFiles = false
    )

    val expanded = mode.expand(
      env = Map(
        "A" -> "B",
        "C" -> "2",
        "D" -> "",
        "STASIS_CLIENT_BOOTSTRAP_SERVER_URL" -> "https://new-server-url",
        "STASIS_CLIENT_BOOTSTRAP_CODE" -> "new-code",
        "STASIS_CLIENT_BOOTSTRAP_USER_NAME" -> "new-user",
        "STASIS_CLIENT_BOOTSTRAP_USER_PASSWORD" -> "new-password"
      )
    )

    expanded.serverBootstrapUrl should be("https://new-server-url")
    expanded.bootstrapCode should be("new-code")
    expanded.acceptSelfSignedCertificates should be(mode.acceptSelfSignedCertificates)
    expanded.userName should be("new-user")
    expanded.userPassword.mkString should be("new-password")
    expanded.recreateFiles should be(mode.recreateFiles)
  }

  they should "expand bootstrap mode arguments from system env vars" in {
    val mode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = "test-user",
      userPassword = Array.emptyCharArray,
      recreateFiles = false
    )

    val expanded = mode.expand()

    expanded.serverBootstrapUrl should be(mode.serverBootstrapUrl)
    expanded.bootstrapCode should be(mode.bootstrapCode)
    expanded.acceptSelfSignedCertificates should be(mode.acceptSelfSignedCertificates)
    expanded.userName should be(mode.userName)
    expanded.userPassword should be(mode.userPassword)
    expanded.recreateFiles should be(mode.recreateFiles)
  }

  they should "validate bootstrap mode arguments" in {
    val mode = ApplicationArguments.Mode.Bootstrap(
      serverBootstrapUrl = "https://test-url",
      bootstrapCode = "test-code",
      acceptSelfSignedCertificates = true,
      userName = "test-user",
      userPassword = Array.emptyCharArray,
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
      recreateFiles = false
    )

    an[IllegalArgumentException] should be thrownBy {
      mode.validate()
    }
  }

  they should "expand maintenance mode arguments from provided env vars (Empty)" in {
    val mode = ApplicationArguments.Mode.Maintenance.Empty

    mode.expand(env = Map("a" -> "b")) should be(mode)
  }

  they should "expand maintenance mode arguments from provided env vars (RegenerateApiCertificate)" in {
    val mode = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate

    mode.expand(env = Map("a" -> "b")) should be(mode)
  }

  they should "expand maintenance mode arguments from provided env vars (PushDeviceSecret)" in {
    val mode = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val expanded = mode
      .expand(
        env = Map(
          "A" -> "B",
          "C" -> "2",
          "D" -> "",
          "STASIS_CLIENT_PUSH_DEVICE_SECRET_CURRENT_USER_NAME" -> "new-user",
          "STASIS_CLIENT_PUSH_DEVICE_SECRET_CURRENT_USER_PASSWORD" -> "new-password",
          "STASIS_CLIENT_PUSH_DEVICE_SECRET_REMOTE_PASSWORD" -> "new-remote-password"
        )
      )
      .asInstanceOf[ApplicationArguments.Mode.Maintenance.PushDeviceSecret]

    expanded.currentUserName should be("new-user")
    expanded.currentUserPassword.mkString should be("new-password")
    expanded.remotePassword.map(_.mkString) should be(Some("new-remote-password"))
  }

  they should "expand maintenance mode arguments from provided env vars (PullDeviceSecret)" in {
    val mode = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val expanded = mode
      .expand(
        env = Map(
          "A" -> "B",
          "C" -> "2",
          "D" -> "",
          "STASIS_CLIENT_PULL_DEVICE_SECRET_CURRENT_USER_NAME" -> "new-user",
          "STASIS_CLIENT_PULL_DEVICE_SECRET_CURRENT_USER_PASSWORD" -> "new-password",
          "STASIS_CLIENT_PULL_DEVICE_SECRET_REMOTE_PASSWORD" -> "new-remote-password"
        )
      )
      .asInstanceOf[ApplicationArguments.Mode.Maintenance.PullDeviceSecret]

    expanded.currentUserName should be("new-user")
    expanded.currentUserPassword.mkString should be("new-password")
    expanded.remotePassword.map(_.mkString) should be(Some("new-remote-password"))
  }

  they should "expand maintenance mode arguments from provided env vars (ReEncryptDeviceSecret)" in {
    val mode = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      oldUserPassword = "other-password".toCharArray
    )

    val expanded = mode
      .expand(
        env = Map(
          "A" -> "B",
          "C" -> "2",
          "D" -> "",
          "STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_CURRENT_USER_NAME" -> "new-user",
          "STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_CURRENT_USER_PASSWORD" -> "new-password",
          "STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_OLD_USER_PASSWORD" -> "new-old-password"
        )
      )
      .asInstanceOf[ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret]

    expanded.currentUserName should be("new-user")
    expanded.currentUserPassword.mkString should be("new-password")
    expanded.oldUserPassword.mkString should be("new-old-password")
  }

  they should "expand maintenance mode arguments from provided env vars (ResetUserCredentials)" in {
    val mode = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = "other-password".toCharArray,
      newUserSalt = "test-salt"
    )

    val expanded = mode
      .expand(
        env = Map(
          "A" -> "B",
          "C" -> "2",
          "D" -> "",
          "STASIS_CLIENT_RESET_USER_CREDENTIALS_CURRENT_USER_PASSWORD" -> "new-password",
          "STASIS_CLIENT_RESET_USER_CREDENTIALS_NEW_USER_PASSWORD" -> "new-other-password",
          "STASIS_CLIENT_RESET_USER_CREDENTIALS_NEW_USER_SALT" -> "new-salt"
        )
      )
      .asInstanceOf[ApplicationArguments.Mode.Maintenance.ResetUserCredentials]

    expanded.currentUserPassword.mkString should be("new-password")
    expanded.newUserPassword.mkString should be("new-other-password")
    expanded.newUserSalt should be("new-salt")
  }

  they should "expand maintenance mode arguments from system env vars (Empty)" in {
    val mode = ApplicationArguments.Mode.Maintenance.Empty

    mode.expand() should be(mode)
  }

  they should "expand maintenance mode arguments from system env vars (RegenerateApiCertificate)" in {
    val mode = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate

    mode.expand() should be(mode)
  }

  they should "expand maintenance mode arguments from system env vars (PushDeviceSecret)" in {
    val mode = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val expanded = mode.expand().asInstanceOf[ApplicationArguments.Mode.Maintenance.PushDeviceSecret]

    expanded.currentUserName should be(mode.currentUserName)
    expanded.currentUserPassword should be(mode.currentUserPassword)
    expanded.remotePassword should be(mode.remotePassword)
  }

  they should "expand maintenance mode arguments from system env vars (PullDeviceSecret)" in {
    val mode = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val expanded = mode.expand().asInstanceOf[ApplicationArguments.Mode.Maintenance.PullDeviceSecret]

    expanded.currentUserName should be(mode.currentUserName)
    expanded.currentUserPassword should be(mode.currentUserPassword)
    expanded.remotePassword should be(mode.remotePassword)
  }

  they should "expand maintenance mode arguments from system env vars (ReEncryptDeviceSecret)" in {
    val mode = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      oldUserPassword = "other-password".toCharArray
    )

    val expanded = mode
      .expand()
      .asInstanceOf[ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret]

    expanded.currentUserName should be(mode.currentUserName)
    expanded.currentUserPassword should be(mode.currentUserPassword)
    expanded.oldUserPassword should be(mode.oldUserPassword)
  }

  they should "expand maintenance mode arguments from system env vars (ResetUserCredentials)" in {
    val mode = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = "other-password".toCharArray,
      newUserSalt = "test-salt"
    )

    val expanded = mode
      .expand()
      .asInstanceOf[ApplicationArguments.Mode.Maintenance.ResetUserCredentials]

    expanded.currentUserPassword should be(mode.currentUserPassword)
    expanded.newUserPassword should be(mode.newUserPassword)
    expanded.newUserSalt should be(mode.newUserSalt)
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
