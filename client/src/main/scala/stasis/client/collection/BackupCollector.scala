package stasis.client.collection

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.model.SourceFile

trait BackupCollector {
  def collect(): Source[SourceFile, NotUsed]
}
