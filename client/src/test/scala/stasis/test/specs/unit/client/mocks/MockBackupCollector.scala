package stasis.test.specs.unit.client.mocks

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.BackupCollector
import stasis.client.model.SourceEntity

class MockBackupCollector(files: List[SourceEntity]) extends BackupCollector {
  override def collect(): Source[SourceEntity, NotUsed] = Source(files)
}
