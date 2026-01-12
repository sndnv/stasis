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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)

            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions,
                result = success,
                duration = 1
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions,
                result = success,
                duration = 2
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions,
                result = failure,
                duration = 3
            )

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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (1)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (3)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = UUID.randomUUID()),
                result = success,
                duration = 4
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = UUID.randomUUID()),
                result = failure,
                duration = 5
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (4)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (5)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.LatestDatasetEntry(definition = UUID.randomUUID()),
                result = success,
                duration = 6
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.LatestDatasetEntry(definition = UUID.randomUUID()),
                result = failure,
                duration = 7
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (6)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (7)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(definition = UUID.randomUUID()),
                result = success,
                duration = 8
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(definition = UUID.randomUUID()),
                result = failure,
                duration = 9
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (8)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (9)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)


            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = UUID.randomUUID()),
                result = success,
                duration = 10
            )
            stats.withRefreshResult(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = UUID.randomUUID()),
                result = failure,
                duration = 11
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

            stats.targets["refreshed_all_dataset_definitions"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_definitions"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_all_dataset_entries"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_latest_dataset_entry"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.minDuration shouldBe (Long.MAX_VALUE)
            stats.targets["refreshed_individual_dataset_definition"]?.maxDuration shouldBe (Long.MIN_VALUE)
            stats.targets["refreshed_individual_dataset_entry"]?.minDuration shouldBe (10)
            stats.targets["refreshed_individual_dataset_entry"]?.maxDuration shouldBe (11)
        }
    }
})
