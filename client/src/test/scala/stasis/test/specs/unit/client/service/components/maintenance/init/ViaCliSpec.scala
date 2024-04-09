package stasis.test.specs.unit.client.service.components.maintenance.init

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.init.ViaCli
import stasis.test.specs.unit.AsyncUnitSpec

class ViaCliSpec extends AsyncUnitSpec {
  "An Init via CLI" should "support retrieving credentials" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userName = "test-user",
      userPassword = "test-password".toCharArray
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .map { case (actualName, actualPassword) =>
        actualName should be(expectedArgs.userName)
        actualPassword.mkString should be(expectedArgs.userPassword.mkString)
      }
  }

  it should "fail to retrieve credentials if no user name is provided" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userName = "",
      userPassword = "test-password".toCharArray
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .failed
      .map { e =>
        e.getMessage should include("User name cannot be empty")
      }
  }

  it should "fail to retrieve credentials if no user password is provided" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance(
      regenerateApiCertificate = false,
      deviceSecretOperation = None,
      userName = "test-user",
      userPassword = Array.emptyCharArray
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .failed
      .map { e =>
        e.getMessage should include("User password cannot be empty")
      }
  }
}
