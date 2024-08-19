package stasis.test.specs.unit.client.service

import stasis.client.service.ApplicationRuntimeRequirements
import stasis.test.specs.unit.AsyncUnitSpec

class ApplicationRuntimeRequirementsSpec extends AsyncUnitSpec {
  "ApplicationRuntimeRequirements" should "support validating JVM versions" in {
    noException should be thrownBy ApplicationRuntimeRequirements.JavaVersion.validate(actual = None, minimum = 17).await
    noException should be thrownBy ApplicationRuntimeRequirements.JavaVersion.validate(actual = Some(17), minimum = 17).await
    noException should be thrownBy ApplicationRuntimeRequirements.JavaVersion.validate(actual = Some(21), minimum = 17).await

    val e = ApplicationRuntimeRequirements.JavaVersion.validate(actual = Some(11), minimum = 17).failed.await
    e.getMessage should be("Current JVM version is [11] but at least [17] is required")
  }

  they should "successfully validate the current JVM version" in {
    noException should be thrownBy ApplicationRuntimeRequirements.JavaVersion.validate().await
  }

  they should "successfully validate the current requirements" in {
    noException should be thrownBy ApplicationRuntimeRequirements.validate().await
  }
}
