package stasis.test.specs.unit.client.mocks

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.client.model.FilesystemMetadata
import stasis.client.ops.search.Search
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.client.mocks.MockSearch.Statistic

class MockSearch() extends Search {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.SearchExecuted -> new AtomicInteger(0)
  )

  override def search(query: Search.Query, until: Option[Instant]): Future[Search.Result] = {
    stats(Statistic.SearchExecuted).incrementAndGet()
    Future.successful(
      Search.Result(
        definitions = Map(
          DatasetDefinition.generateId() -> Some(
            Search.DatasetDefinitionResult(
              definitionInfo = "test-info",
              entryId = DatasetEntry.generateId(),
              entryCreated = Instant.now(),
              matches = Map(
                Paths.get("file-01") -> FilesystemMetadata.EntityState.New,
                Paths.get("file-02") -> FilesystemMetadata.EntityState.Updated,
                Paths.get("file-03") -> FilesystemMetadata.EntityState.Existing(entry = DatasetEntry.generateId())
              )
            )
          ),
          DatasetDefinition.generateId() -> None
        )
      )
    )
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockSearch {
  def apply(): MockSearch = new MockSearch()

  sealed trait Statistic
  object Statistic {
    case object SearchExecuted extends Statistic
  }
}
