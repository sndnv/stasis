package stasis.test.specs.unit.client.mocks

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetEntity

class MockRecoveryCollector(files: List[TargetEntity]) extends RecoveryCollector {
  override def collect(): Source[TargetEntity, NotUsed] = Source(files)
}
