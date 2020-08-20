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

  it should "support retrieving user password" in {
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
      )
      .map { password =>
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "skip asking for user password if it's already provided" in {
    val expectedUserPassword = "test-password".toCharArray

    val console = mock[java.io.Console]

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty.copy(userPassword = expectedUserPassword)
      )
      .map { password =>
        password.mkString should be(expectedUserPassword.mkString)
      }
  }

  it should "fail if no or empty user password is provided" in {
    val expectedUserPassword = Array.emptyCharArray

    val console = mock[java.io.Console]
    when(console.readPassword("User Password: ")).thenReturn(expectedUserPassword)

    ViaStdIn
      .retrieveCredentials(
        console = console,
        args = ApplicationArguments.Mode.Bootstrap.empty
      )
      .failed
      .map { e =>
        e.getMessage should include("User password cannot be empty")
      }
  }
}
