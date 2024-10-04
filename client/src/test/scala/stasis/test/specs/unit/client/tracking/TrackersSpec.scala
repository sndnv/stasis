package stasis.test.specs.unit.client.tracking

import stasis.client.tracking.Trackers
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.mocks.MockBackupTracker
import stasis.test.specs.unit.client.mocks.MockRecoveryTracker
import stasis.test.specs.unit.client.mocks.MockServerTracker

class TrackersSpec extends UnitSpec {
  "Trackers" should "provide tracker views" in {
    val trackers = Trackers(
      backup = new MockBackupTracker(),
      recovery = new MockRecoveryTracker(),
      server = new MockServerTracker()
    )

    val views = trackers.views

    views.backup should be(trackers.backup)
    views.recovery should be(trackers.recovery)
    views.server should be(trackers.server)
  }
}
