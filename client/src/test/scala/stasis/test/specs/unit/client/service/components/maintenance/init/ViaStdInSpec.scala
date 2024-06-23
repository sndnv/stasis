package stasis.test.specs.unit.client.service.components.maintenance.init

import org.mockito.scalatest.AsyncMockitoSugar
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.init.ViaStdIn
import stasis.test.specs.unit.AsyncUnitSpec

class ViaStdInSpec extends AsyncUnitSpec with AsyncMockitoSugar {
  "An Init via StdIn" should "support retrieving current user name and password" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCurrentCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .map { case (name, password) =>
        name should be(expectedUserName)
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "skip asking for current user name if it's already provided" in {
    val expectedUserName = "test-name"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("Current User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCurrentCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty.copy(currentUserName = expectedUserName)
      )
      .map { case (name, password) =>
        name.mkString should be(expectedUserName.mkString)
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "skip asking for current user password if it's already provided" in {
    val expectedUserName = "test-name"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedUserName)

    ViaStdIn
      .retrieveCurrentCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty.copy(currentUserPassword = expectedUserPassword)
      )
      .map { case (name, password) =>
        name.mkString should be(expectedUserName.mkString)
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "fail if no or empty current user name is provided" in {
    val expectedUserName = "   "
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCurrentCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user name cannot be empty")
      }
  }

  it should "fail if no or empty current user password is provided" in {
    val expectedUserName = "test-name"
    val expectedUserPassword = Array.emptyCharArray

    val console = mock[java.io.Console]
    when(console.readLine("Current User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("Current User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCurrentCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Current user password cannot be empty")
      }
  }

  it should "support retrieving new user name and password" in {
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserSalt = "test-salt"

    val console = mock[java.io.Console]
    when(console.readPassword("New User Password: ")).thenReturn(expectedUserPassword)
    when(console.readLine("New User Salt: ")).thenReturn(expectedUserSalt)

    ViaStdIn
      .retrieveNewCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .map { case (password, salt) =>
        password.mkString should be(expectedUserPassword.mkString)
        salt should be(expectedUserSalt)
      }
  }

  it should "skip asking for new user password if it's already provided" in {
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserSalt = "test-salt"

    val console = mock[java.io.Console]
    when(console.readLine("New User Salt: ")).thenReturn(expectedUserSalt)

    ViaStdIn
      .retrieveNewCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty.copy(newUserPassword = expectedUserPassword)
      )
      .map { case (password, salt) =>
        password.mkString should be(expectedUserPassword.mkString)
        salt should be(expectedUserSalt)
      }
  }

  it should "skip asking for new user salt if it's already provided" in {
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserSalt = "test-salt"

    val console = mock[java.io.Console]
    when(console.readPassword("New User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveNewCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty.copy(newUserSalt = expectedUserSalt)
      )
      .map { case (password, salt) =>
        password.mkString should be(expectedUserPassword.mkString)
        salt should be(expectedUserSalt)
      }
  }

  it should "fail if no or empty new user password is provided" in {
    val expectedUserPassword = Array.emptyCharArray
    val expectedUserSalt = "test-salt"

    val console = mock[java.io.Console]
    when(console.readPassword("New User Password: ")).thenReturn(expectedUserPassword)
    when(console.readLine("New User Salt: ")).thenReturn(expectedUserSalt)

    ViaStdIn
      .retrieveNewCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("New user password cannot be empty")
      }
  }

  it should "fail if no or empty new user salt is provided" in {
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserSalt = "   "

    val console = mock[java.io.Console]
    when(console.readPassword("New User Password: ")).thenReturn(expectedUserPassword)
    when(console.readLine("New User Salt: ")).thenReturn(expectedUserSalt)

    ViaStdIn
      .retrieveNewCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("New user salt cannot be empty")
      }
  }
}
