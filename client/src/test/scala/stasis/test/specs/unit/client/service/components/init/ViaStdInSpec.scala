package stasis.test.specs.unit.client.service.components.init

import scala.util.control.NonFatal

import org.mockito.scalatest.AsyncMockitoSugar

import stasis.client.service.components.init.ViaStdIn
import stasis.test.specs.unit.AsyncUnitSpec

class ViaStdInSpec extends AsyncUnitSpec with AsyncMockitoSugar {
  "An Init via StdIn" should "support retrieving credentials" in {
    val expectedUsername = "test-username"
    val expectedPassword = "test-password"

    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn(expectedUsername)
    when(console.readPassword("Password: ")).thenReturn(expectedPassword.toCharArray)

    ViaStdIn
      .retrieve(console)
      .map { case (actualUsername, actualPassword) =>
        actualUsername should be(expectedUsername)
        expectedPassword should be(new String(actualPassword))
      }
  }

  it should "reject credentials with an empty username" in {
    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn("")
    when(console.readPassword("Password: ")).thenReturn("test-password".toCharArray)

    ViaStdIn
      .retrieve(console)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be("Empty username and/or password provided")
      }
  }

  it should "reject credentials with an empty password" in {
    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn("test-username")
    when(console.readPassword("Password: ")).thenReturn("".toCharArray)

    ViaStdIn
      .retrieve(console)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be("Empty username and/or password provided")
      }
  }
}
