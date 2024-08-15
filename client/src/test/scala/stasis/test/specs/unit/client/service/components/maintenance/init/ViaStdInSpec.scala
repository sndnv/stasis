package stasis.test.specs.unit.client.service.components.maintenance.init

import org.mockito.scalatest.AsyncMockitoSugar
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.init.ViaStdIn
import stasis.test.specs.unit.AsyncUnitSpec

class ViaStdInSpec extends AsyncUnitSpec with AsyncMockitoSugar {
  "An Init via StdIn" should "support retrieving credentials (ResetUserCredentials)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = "other-password".toCharArray,
      newUserSalt = "some-salt"
    )

    val console = mock[java.io.Console]
    when(console.readPassword("Current User Password: ")).thenReturn(expectedArgs.currentUserPassword)
    when(console.readPassword("New User Password: ")).thenReturn(expectedArgs.newUserPassword)
    when(console.readLine("New User Salt: ")).thenReturn(expectedArgs.newUserSalt)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ResetUserCredentials.empty
      )
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support skipping parameters that have already been provided (ResetUserCredentials)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = "other-password".toCharArray,
      newUserSalt = "some-salt"
    )

    val console = mock[java.io.Console]
    when(console.readPassword("Current User Password: ")).thenReturn(expectedArgs.currentUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
          currentUserPassword = Array.emptyCharArray,
          newUserPassword = expectedArgs.newUserPassword,
          newUserSalt = expectedArgs.newUserSalt
        )
      )
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "fail if no or empty current user password is provided (ResetUserCredentials)" in {
    val console = mock[java.io.Console]
    when(console.readPassword("Current User Password: ")).thenReturn(Array.emptyCharArray)
    when(console.readPassword("New User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readLine("New User Salt: ")).thenReturn("test-salt")

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ResetUserCredentials.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user password cannot be empty")
      }
  }

  it should "fail if no or empty new user password is provided (ResetUserCredentials)" in {
    val console = mock[java.io.Console]
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("New User Password: ")).thenReturn(Array.emptyCharArray)
    when(console.readLine("New User Salt: ")).thenReturn("test-salt")

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ResetUserCredentials.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("New user password cannot be empty")
      }
  }

  it should "fail if no or empty new user salt is provided (ResetUserCredentials)" in {
    val console = mock[java.io.Console]
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("New User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readLine("New User Salt: ")).thenReturn("  ")

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ResetUserCredentials.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("New user salt cannot be empty")
      }
  }

  it should "support retrieving credentials (PushDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedArgs.currentUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedArgs.currentUserPassword)
    when(console.readPassword("Remote Password (optional): "))
      .thenReturn(expectedArgs.remotePassword.getOrElse(Array.emptyCharArray))

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PushDeviceSecret.empty
      )
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support skipping parameters that have already been provided (PushDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedArgs.currentUserName)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
          currentUserName = "",
          currentUserPassword = "test-password".toCharArray,
          remotePassword = Some("other-password".toCharArray)
        )
      )
      .map {
        case args: ApplicationArguments.Mode.Maintenance.PushDeviceSecret =>
          args.currentUserName should be(expectedArgs.currentUserName)
          args.currentUserPassword should be(expectedArgs.currentUserPassword)
          args.remotePassword.map(new String(_)) should be(expectedArgs.remotePassword.map(new String(_)))

        case other =>
          fail(s"Unexpected result received [$other]")
      }
  }

  it should "fail if no or empty current user name is provided (PushDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("")
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("Remote Password (optional): ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PushDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user name cannot be empty")
      }
  }

  it should "fail if no or empty current user password is provided (PushDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("test-name")
    when(console.readPassword("Current User Password: ")).thenReturn(Array.emptyCharArray)
    when(console.readPassword("Remote Password (optional): ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PushDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user password cannot be empty")
      }
  }

  it should "accept no or empty optional remote password (PushDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("test-name")
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("Remote Password (optional): ")).thenReturn(Array.emptyCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PushDeviceSecret.empty
      )
      .map {
        case args: ApplicationArguments.Mode.Maintenance.PushDeviceSecret =>
          args.currentUserName should be("test-name")
          new String(args.currentUserPassword) should be("test-password")
          args.remotePassword should be(empty)

        case other =>
          fail(s"Unexpected result received [$other]")
      }
  }

  it should "support retrieving credentials (PullDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedArgs.currentUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedArgs.currentUserPassword)
    when(console.readPassword("Remote Password (optional): "))
      .thenReturn(expectedArgs.remotePassword.getOrElse(Array.emptyCharArray))

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PullDeviceSecret.empty
      )
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support skipping parameters that have already been provided (PullDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = Some("other-password".toCharArray)
    )

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedArgs.currentUserName)
    when(console.readPassword("Remote Password (optional): "))
      .thenReturn(expectedArgs.remotePassword.getOrElse(Array.emptyCharArray))

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
          currentUserName = "",
          currentUserPassword = "test-password".toCharArray,
          remotePassword = None
        )
      )
      .map {
        case args: ApplicationArguments.Mode.Maintenance.PullDeviceSecret =>
          args.currentUserName should be(expectedArgs.currentUserName)
          args.currentUserPassword should be(expectedArgs.currentUserPassword)
          args.remotePassword should be(expectedArgs.remotePassword)

        case other =>
          fail(s"Unexpected result received [$other]")
      }
  }

  it should "fail if no or empty current user name is provided (PullDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("")
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("Remote Password (optional): ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PullDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user name cannot be empty")
      }
  }

  it should "fail if no or empty current user password is provided (PullDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("test-name")
    when(console.readPassword("Current User Password: ")).thenReturn(Array.emptyCharArray)
    when(console.readPassword("Remote Password (optional): ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PullDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user password cannot be empty")
      }
  }

  it should "accept no or empty optional remote password (PullDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("test-name")
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("Remote Password (optional): ")).thenReturn(Array.emptyCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.PullDeviceSecret.empty
      )
      .map {
        case args: ApplicationArguments.Mode.Maintenance.PullDeviceSecret =>
          args.currentUserName should be("test-name")
          new String(args.currentUserPassword) should be("test-password")
          args.remotePassword should be(None)

        case other =>
          fail(s"Unexpected result received [$other]")
      }
  }

  it should "support retrieving credentials (ReEncryptDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      oldUserPassword = "other-password".toCharArray
    )

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedArgs.currentUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedArgs.currentUserPassword)
    when(console.readPassword("Old User Password: ")).thenReturn(expectedArgs.oldUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret.empty
      )
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support skipping parameters that have already been provided (ReEncryptDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      oldUserPassword = "other-password".toCharArray
    )

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedArgs.currentUserName)
    when(console.readPassword("Old User Password: ")).thenReturn(expectedArgs.oldUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
          currentUserName = "",
          currentUserPassword = "test-password".toCharArray,
          oldUserPassword = Array.emptyCharArray
        )
      )
      .map {
        case args: ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret =>
          args.currentUserName should be(expectedArgs.currentUserName)
          args.currentUserPassword should be(expectedArgs.currentUserPassword)
          args.oldUserPassword should be(expectedArgs.oldUserPassword)

        case other =>
          fail(s"Unexpected result received [$other]")
      }
  }

  it should "fail if no or empty current user name is provided (ReEncryptDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("")
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("Old User Password: ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user name cannot be empty")
      }
  }

  it should "fail if no or empty current user password is provided (ReEncryptDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("test-name")
    when(console.readPassword("Current User Password: ")).thenReturn(Array.emptyCharArray)
    when(console.readPassword("Old User Password: ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user password cannot be empty")
      }
  }

  it should "fail if no or empty old user password is provided (ReEncryptDeviceSecret)" in {
    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn("test-name")
    when(console.readPassword("Current User Password: ")).thenReturn("test-password".toCharArray)
    when(console.readPassword("Old User Password: ")).thenReturn(Array.emptyCharArray)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Old user password cannot be empty")
      }
  }

  it should "do nothing if credentials are not required" in {
    val console = mock[java.io.Console]

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.Empty
      )
      .await should be(
      ApplicationArguments.Mode.Maintenance.Empty
    )

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate
      )
      .await should be(
      ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate
    )
  }
}
