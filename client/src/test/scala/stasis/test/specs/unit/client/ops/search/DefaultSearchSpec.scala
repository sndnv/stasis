package stasis.test.specs.unit.client.ops.search

import java.nio.file.Paths
import java.time.Instant

import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.client.ops.search.{DefaultSearch, Search}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.shared.model.Generators

import scala.concurrent.Future

class DefaultSearchSpec extends AsyncUnitSpec {
  "A DefaultSearch" should "perform searchers on dataset metadata" in {
    val matchingDefinition = DatasetDefinition.generateId()
    val nonMatchingDefinition = DatasetDefinition.generateId()
    val missingEntryDefinition = DatasetDefinition.generateId()

    val matchingEntry = DatasetEntry.generateId()

    val searchTerm = "test-file-name"

    val definitions = Seq(
      Generators.generateDefinition.copy(id = matchingDefinition),
      Generators.generateDefinition.copy(id = nonMatchingDefinition),
      Generators.generateDefinition.copy(id = missingEntryDefinition)
    )

    val matchingFiles = Map(
      Paths.get(s"$searchTerm-01") -> FilesystemMetadata.EntityState.New,
      Paths.get(s"$searchTerm-02") -> FilesystemMetadata.EntityState.Updated,
      Paths.get(s"other-$searchTerm") -> FilesystemMetadata.EntityState.Existing(DatasetEntry.generateId()),
      Paths.get(s"$searchTerm") -> FilesystemMetadata.EntityState.New
    )

    val nonMatchingFiles = Map(
      Paths.get("other-name") -> FilesystemMetadata.EntityState.New
    )

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def datasetDefinitions(): Future[Seq[DatasetDefinition]] =
        super.datasetDefinitions().map(_ => definitions)

      override def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]] =
        super.latestEntry(definition, until).map {
          case Some(entry) if definition == matchingDefinition    => Some(entry.copy(id = matchingEntry)) // match
          case Some(entry) if definition == nonMatchingDefinition => Some(entry) // no match
          case Some(_)                                            => None // no latest entry
          case None                                               => None // no entry generated
        }

      override def datasetMetadata(entry: DatasetEntry): Future[DatasetMetadata] =
        if (entry.id == matchingEntry) {
          super
            .datasetMetadata(entry)
            .map(_.copy(filesystem = FilesystemMetadata(entities = matchingFiles ++ nonMatchingFiles)))
        } else {
          super.datasetMetadata(entry)
        }
    }

    val search = new DefaultSearch(api = mockApiClient)

    search
      .search(
        query = Search.Query(searchTerm),
        until = None
      )
      .map { result =>
        result.definitions.size should be(definitions.size)

        result.definitions(matchingDefinition) match {
          case Some(Search.DatasetDefinitionResult(_, `matchingEntry`, _, matches)) =>
            matches should be(matchingFiles)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        result.definitions(nonMatchingDefinition) should be(None)

        result.definitions(missingEntryDefinition) should be(None)

        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(3)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(1)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(2)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
      }
  }
}
