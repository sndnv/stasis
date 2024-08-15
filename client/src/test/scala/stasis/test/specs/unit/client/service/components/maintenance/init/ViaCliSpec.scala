package stasis.test.specs.unit.client.service.components.maintenance.init

import stasis.client.service.ApplicationArguments
import stasis.client.service.components.maintenance.init.ViaCli
import stasis.test.specs.unit.AsyncUnitSpec

class ViaCliSpec extends AsyncUnitSpec {
  "An Init via CLI" should "support validating credentials (ResetUserCredentials)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = "other-password".toCharArray,
      newUserSalt = "some-salt"
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support validating credentials (PushDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = None
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support validating credentials (PullDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = None
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "support validating credentials (ReEncryptDeviceSecret)" in {
    val expectedArgs = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      oldUserPassword = "other-password".toCharArray
    )

    ViaCli
      .retrieveCredentials(args = expectedArgs)
      .map { args =>
        args should be(expectedArgs)
      }
  }

  it should "fail to retrieve credentials if required values are missing (ResetUserCredentials)" in {
    val args = ApplicationArguments.Mode.Maintenance.ResetUserCredentials(
      currentUserPassword = "test-password".toCharArray,
      newUserPassword = "other-password".toCharArray,
      newUserSalt = "some-salt"
    )

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserPassword = Array.emptyCharArray))
      .failed
      .await
      .getMessage should include("Current user password cannot be empty")

    ViaCli
      .retrieveCredentials(args = args.copy(newUserPassword = Array.emptyCharArray))
      .failed
      .await
      .getMessage should include("New user password cannot be empty")

    ViaCli
      .retrieveCredentials(args = args.copy(newUserSalt = ""))
      .failed
      .await
      .getMessage should include("New user salt cannot be empty")
  }

  it should "fail to retrieve credentials if required values are missing (PushDeviceSecret)" in {
    val args = ApplicationArguments.Mode.Maintenance.PushDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = None
    )

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserName = ""))
      .failed
      .await
      .getMessage should include("Current user name cannot be empty")

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserPassword = Array.emptyCharArray))
      .failed
      .await
      .getMessage should include("Current user password cannot be empty")
  }

  it should "fail to retrieve credentials if required values are missing (PullDeviceSecret)" in {
    val args = ApplicationArguments.Mode.Maintenance.PullDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      remotePassword = None
    )

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserName = ""))
      .failed
      .await
      .getMessage should include("Current user name cannot be empty")

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserPassword = Array.emptyCharArray))
      .failed
      .await
      .getMessage should include("Current user password cannot be empty")
  }

  it should "fail to retrieve credentials if required values are missing (ReEncryptDeviceSecret)" in {
    val args = ApplicationArguments.Mode.Maintenance.ReEncryptDeviceSecret(
      currentUserName = "some-name",
      currentUserPassword = "test-password".toCharArray,
      oldUserPassword = "other-password".toCharArray
    )

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserName = ""))
      .failed
      .await
      .getMessage should include("Current user name cannot be empty")

    ViaCli
      .retrieveCredentials(args = args.copy(currentUserPassword = Array.emptyCharArray))
      .failed
      .await
      .getMessage should include("Current user password cannot be empty")

    ViaCli
      .retrieveCredentials(args = args.copy(oldUserPassword = Array.emptyCharArray))
      .failed
      .await
      .getMessage should include("Old user password cannot be empty")
  }

  it should "do nothing if credentials are not required" in {
    ViaCli.retrieveCredentials(args = ApplicationArguments.Mode.Maintenance.Empty).await should be(
      ApplicationArguments.Mode.Maintenance.Empty
    )

    ViaCli.retrieveCredentials(args = ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate).await should be(
      ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate
    )
  }
}
