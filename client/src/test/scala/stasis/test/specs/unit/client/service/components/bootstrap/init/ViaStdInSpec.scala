package stasis.test.specs.unit.client.service.components.bootstrap.init

import org.mockito.scalatest.AsyncMockitoSugar
import stasis.client.service.ApplicationArguments
import stasis.client.service.components.bootstrap.init.ViaStdIn
import stasis.test.specs.unit.AsyncUnitSpec

class ViaStdInSpec extends AsyncUnitSpec with AsyncMockitoSugar {
  "An Init via StdIn" should "support retrieving all bootstrap mode arguments" in {
    val expectedServerBootstrapUrl = "https://test-url"
    val expectedBootstrapCode = "test-code"

    val console = mock[java.io.Console]
    when(console.readLine("Server bootstrap URL: ")).thenReturn(expectedServerBootstrapUrl)
    when(console.readLine("Bootstrap Code: ")).thenReturn(expectedBootstrapCode)

    ViaStdIn
      .retrieveArguments(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
      )
      .map { args =>
        args.serverBootstrapUrl should be(expectedServerBootstrapUrl)
        args.bootstrapCode should be(expectedBootstrapCode)
      }
  }

  it should "skip asking for server bootstrap URL if it's already provided" in {
    val expectedServerBootstrapUrl = "https://test-url"
    val expectedBootstrapCode = "test-code"

    val console = mock[java.io.Console]
    when(console.readLine("Bootstrap Code: ")).thenReturn(expectedBootstrapCode)

    ViaStdIn
      .retrieveArguments(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(serverBootstrapUrl = expectedServerBootstrapUrl)
      )
      .map { args =>
        args.serverBootstrapUrl should be(expectedServerBootstrapUrl)
        args.bootstrapCode should be(expectedBootstrapCode)
      }
  }

  it should "skip asking for bootstrap code if it's already provided" in {
    val expectedServerBootstrapUrl = "https://test-url"
    val expectedBootstrapCode = "test-code"

    val console = mock[java.io.Console]
    when(console.readLine("Server bootstrap URL: ")).thenReturn(expectedServerBootstrapUrl)

    ViaStdIn
      .retrieveArguments(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(bootstrapCode = expectedBootstrapCode)
      )
      .map { args =>
        args.serverBootstrapUrl should be(expectedServerBootstrapUrl)
        args.bootstrapCode should be(expectedBootstrapCode)
      }
  }

  it should "fail if invalid server bootstrap URL is provided" in {
    val expectedServerBootstrapUrl = "http://test-url"
    val expectedBootstrapCode = "test-code"

    val console = mock[java.io.Console]
    when(console.readLine("Server bootstrap URL: ")).thenReturn(expectedServerBootstrapUrl)
    when(console.readLine("Bootstrap Code: ")).thenReturn(expectedBootstrapCode)

    ViaStdIn
      .retrieveArguments(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Server bootstrap URL must be provided and must use HTTPS")
      }
  }

  it should "fail if no or empty bootstrap code is provided" in {
    val expectedServerBootstrapUrl = "https://test-url"
    val expectedBootstrapCode = ""

    val console = mock[java.io.Console]
    when(console.readLine("Server bootstrap URL: ")).thenReturn(expectedServerBootstrapUrl)
    when(console.readLine("Bootstrap Code: ")).thenReturn(expectedBootstrapCode)

    ViaStdIn
      .retrieveArguments(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("Bootstrap code must be provided")
      }
  }

  it should "support retrieving user name and password" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserPasswordConfirmation = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readLine("User Name: ")).thenReturn(expectedUserName)
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)
    when(console.readPassword("Confirm Password: ")).thenReturn(expectedUserPasswordConfirmation)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
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
    when(console.readPassword("Confirm Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(userName = expectedUserName)
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
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(userPassword = expectedUserPassword)
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
    when(console.readPassword("Confirm Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("User name cannot be empty")
      }
  }

  it should "fail if no or empty user password is provided" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = Array.emptyCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)
    when(console.readPassword("Confirm Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(userName = expectedUserName)
      )
      .failed
      .map { e =>
        e.getMessage should include("User password cannot be empty")
      }
  }

  it should "fail if the provided passwords do not match" in {
    val expectedUserName = "test-user"
    val expectedUserPassword = "test-password".toCharArray
    val expectedUserPasswordConfirmation = "other-test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)
    when(console.readPassword("Confirm Password: ")).thenReturn(expectedUserPasswordConfirmation)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(userName = expectedUserName)
      )
      .failed
      .map { e =>
        e.getMessage should include("Passwords do not match")
      }
  }
}
