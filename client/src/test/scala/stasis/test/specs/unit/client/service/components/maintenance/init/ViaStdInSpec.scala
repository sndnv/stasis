package stasis.test.specs.unit.client.service.components.maintenance.init

import org.mockito.scalatest.AsyncMockitoSugar
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.init.ViaStdIn
import stasis.test.specs.unit.AsyncUnitSpec

class ViaStdInSpec extends AsyncUnitSpec with AsyncMockitoSugar {
  "An Init via StdIn" should "support retrieving user name and password" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .map { case (name, password) =>
        name should be(expectedUserName)
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "skip asking for user name if it's already provided" in {
    val expectedUserName = "test-name"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty.copy(userName = expectedUserName)
      )
      .map { case (name, password) =>
        name.mkString should be(expectedUserName.mkString)
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "skip asking for user password if it's already provided" in {
    val expectedUserName = "test-name"
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("User Name: ")).thenReturn(expectedUserName)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty.copy(userPassword = expectedUserPassword)
      )
      .map { case (name, password) =>
        name.mkString should be(expectedUserName.mkString)
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "fail if no or empty user name is provided" in {
    val expectedUserName = "   "
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("User name cannot be empty")
      }
  }

  it should "fail if no or empty user password is provided" in {
    val expectedUserName = "test-name"
    val expectedUserPassword = Array.emptyCharArray

    val console = mock[java.io.Console]
    when(console.readLine("User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Maintenance.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("User password cannot be empty")
      }
  }
}
