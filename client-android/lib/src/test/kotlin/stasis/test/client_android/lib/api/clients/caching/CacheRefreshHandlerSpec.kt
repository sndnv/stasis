package stasis.test.client_android.lib.api.clients.caching

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.api.clients.caching.CacheRefreshHandler
import stasis.client_android.lib.utils.Try
import java.util.UUID

class CacheRefreshHandlerSpec : WordSpec({
    "A CacheRefreshHandler" should {
        val success = Try.Success(Unit)
        val failure = Try.Failure<Unit>(RuntimeException())

        "support providing and updating statistics (all_dataset_definitions)" {
            val stats = CacheRefreshHandler.Statistics()

            stats.lastRefresh shouldBe (null)

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)

            stats.withRefreshResult(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions, result = success)
            stats.withRefreshResult(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions, result = success)
            stats.withRefreshResult(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions, result = failure)

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (2)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (1)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)
        }

        "support providing and updating statistics (all_dataset_entries)" {
            val stats = CacheRefreshHandler.Statistics()

            stats.lastRefresh shouldBe (null)

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = UUID.randomUUID()),
                result = success
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = UUID.randomUUID()),
                result = failure
            )

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (1)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (1)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)
        }

        "support providing and updating statistics (latest_dataset_entry)" {
            val stats = CacheRefreshHandler.Statistics()

            stats.lastRefresh shouldBe (null)

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.LatestDatasetEntry(definition = UUID.randomUUID()),
                result = success
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.LatestDatasetEntry(definition = UUID.randomUUID()),
                result = failure
            )

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (1)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (1)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)
        }

        "support providing and updating statistics (individual_dataset_definition)" {
            val stats = CacheRefreshHandler.Statistics()

            stats.lastRefresh shouldBe (null)

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(definition = UUID.randomUUID()),
                result = success
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(definition = UUID.randomUUID()),
                result = failure
            )

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (1)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (1)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)
        }

        "support providing and updating statistics (individual_dataset_entry)" {
            val stats = CacheRefreshHandler.Statistics()

            stats.lastRefresh shouldBe (null)

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = UUID.randomUUID()),
                result = success
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = UUID.randomUUID()),
                result = failure
            )

            stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)
            stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (1)
            stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (1)
        }
    }
})
