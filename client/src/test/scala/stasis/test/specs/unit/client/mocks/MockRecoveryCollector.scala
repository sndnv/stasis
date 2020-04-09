package stasis.test.specs.unit.client.mocks

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetEntity

class MockRecoveryCollector(files: List[TargetEntity]) extends RecoveryCollector {
  override def collect(): Source[TargetEntity, NotUsed] = Source(files)
}
