package stasis.test.specs.unit.client.mocks

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import stasis.client.collection.BackupCollector
import stasis.client.model.SourceEntity

class MockBackupCollector(files: List[SourceEntity]) extends BackupCollector {
  override def collect(): Source[SourceEntity, NotUsed] = Source(files)
}
