package stasis.test.specs.unit.client.mocks

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.BackupCollector
import stasis.client.model.SourceFile

class MockBackupCollector(files: List[SourceFile]) extends BackupCollector {
  override def collect(): Source[SourceFile, NotUsed] = Source(files)
}
