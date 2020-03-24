package stasis.test.specs.unit.client.mocks

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetFile

class MockRecoveryCollector(files: List[TargetFile]) extends RecoveryCollector {
  override def collect(): Source[TargetFile, NotUsed] = Source(files)
}
