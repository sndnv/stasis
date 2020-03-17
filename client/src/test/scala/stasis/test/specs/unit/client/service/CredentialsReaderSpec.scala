package stasis.test.specs.unit.client.service

import org.mockito.scalatest.MockitoSugar
import stasis.client.service.CredentialsReader
import stasis.test.specs.unit.UnitSpec

import scala.util.{Failure, Success}

class CredentialsReaderSpec extends UnitSpec with MockitoSugar {
  "A username/password CredentialsReader" should "retrieve credentials" in {
    val expectedUsername = "test-username"
    val expectedPassword = "test-password"

    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn(expectedUsername)
    when(console.readPassword("Password: ")).thenReturn(expectedPassword.toCharArray)

    val reader = CredentialsReader.UsernameAndPassword(console = Some(console))

    reader.retrieve() match {
      case Success((actualUsername, actualPassword)) =>
        actualUsername should be(expectedUsername)
        expectedPassword should be(new String(actualPassword))

      case Failure(e) =>
        fail(e)
    }
  }

  it should "reject credentials with an empty username" in {
    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn("")
    when(console.readPassword("Password: ")).thenReturn("test-password".toCharArray)

    val reader = CredentialsReader.UsernameAndPassword(console = Some(console))

    reader.retrieve() match {
      case Failure(e: IllegalArgumentException) =>
        e.getMessage should be("Empty username and/or password provided")

      case result =>
        fail(s"Unexpected result received [$result]")
    }
  }

  it should "reject credentials with an empty password" in {
    val console = mock[java.io.Console]
    when(console.readLine("Username: ")).thenReturn("test-username")
    when(console.readPassword("Password: ")).thenReturn("".toCharArray)

    val reader = CredentialsReader.UsernameAndPassword(console = Some(console))

    reader.retrieve() match {
      case Failure(e: IllegalArgumentException) =>
        e.getMessage should be("Empty username and/or password provided")

      case result =>
        fail(s"Unexpected result received [$result]")
    }
  }

  it should "fail if a console is not available" in {
    val reader = CredentialsReader.UsernameAndPassword(console = None)

    reader.retrieve() match {
      case Failure(e: IllegalStateException) =>
        e.getMessage should be("Console not available; cannot retrieve credentials")

      case result =>
        fail(s"Unexpected result received [$result]")
    }
  }
}
