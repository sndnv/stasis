package stasis.test.specs.unit.client.mocks

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.RecoveryCollector
import stasis.client.model.SourceFile

class MockRecoveryCollector(files: List[SourceFile]) extends RecoveryCollector {
  override def collect(): Source[SourceFile, NotUsed] = Source(files)
}
