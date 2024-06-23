package stasis.test.specs.unit.client.service.components.maintenance.init

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.init.ViaCli
import stasis.test.specs.unit.AsyncUnitSpec

class ViaCliSpec extends AsyncUnitSpec {
  "An Init via CLI" should "support retrieving current credentials" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = "test-user",
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    ViaCli
      .retrieveCurrentCredentials(args = expectedArgs)
      .map { case (actualName, actualPassword) =>
        actualName should be(expectedArgs.currentUserName)
        actualPassword.mkString should be(expectedArgs.currentUserPassword.mkString)
      }
  }

  it should "fail to retrieve current credentials if no user name is provided" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = "",
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    ViaCli
      .retrieveCurrentCredentials(args = expectedArgs)
      .failed
      .map { e =>
        e.getMessage should include("Current user name cannot be empty")
      }
  }

  it should "fail to retrieve current credentials if no user password is provided" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = "test-user",
      currentUserPassword = Array.emptyCharArray,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = ""
    )

    ViaCli
      .retrieveCurrentCredentials(args = expectedArgs)
      .failed
      .map { e =>
        e.getMessage should include("Current user password cannot be empty")
      }
  }

  it should "support retrieving new credentials" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = "",
      currentUserPassword = Array.emptyCharArray,
      newUserPassword = "test-password".toCharArray,
      newUserSalt = "test-salt"
    )

    ViaCli
      .retrieveNewCredentials(args = expectedArgs)
      .map { case (actualPassword, actualSalt) =>
        actualPassword.mkString should be(expectedArgs.newUserPassword.mkString)
        actualSalt should be(expectedArgs.newUserSalt)
      }
  }

  it should "fail to retrieve new credentials if no user password is provided" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = "",
      currentUserPassword = Array.emptyCharArray,
      newUserPassword = Array.emptyCharArray,
      newUserSalt = "test-salt"
    )

    ViaCli
      .retrieveNewCredentials(args = expectedArgs)
      .failed
      .map { e =>
        e.getMessage should include("New user password cannot be empty")
      }
  }

  it should "fail to retrieve new credentials if no user salt is provided" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userCredentialsOperation = None,
      currentUserName = "",
      currentUserPassword = Array.emptyCharArray,
      newUserPassword = "test-password".toCharArray,
      newUserSalt = ""
    )

    ViaCli
      .retrieveNewCredentials(args = expectedArgs)
      .failed
      .map { e =>
        e.getMessage should include("New user salt cannot be empty")
      }
  }
}
