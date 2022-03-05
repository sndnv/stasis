package stasis.client_android.persistence.rules

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.persistence.Converters.Companion.asEntity
import stasis.client_android.persistence.Converters.Companion.asRule

class RuleRepository(private val dao: RuleEntityDao) {
    val rules: LiveData<List<Rule>> =
        dao.get().map { entities -> entities.map { it.asRule() }.sortedBy { it.id } }

    suspend fun rulesAsync(): List<Rule> =
        dao.getAsync().map { it.asRule() }.sortedBy { it.id }

    suspend fun put(rule: Rule): Long =
        put(rule.asEntity())

    suspend fun put(rule: RuleEntity): Long =
        dao.put(rule)

    suspend fun delete(id: Long) =
        dao.delete(id)

    suspend fun bootstrap() {
        DefaultRules.map { rule -> dao.put(rule) }
    }

    suspend fun clear() {
        dao.clear()
    }

    companion object {
        operator fun invoke(context: Context): RuleRepository {
            val dao = RuleEntityDatabase.getInstance(context).dao()
            return RuleRepository(dao)
        }

        val DefaultRules: List<RuleEntity> = listOf(
            RuleEntity(
                operation = Rule.Operation.Include,
                directory = "/storage/emulated/0",
                pattern = "**"
            ), // includes all accessible user storage
            RuleEntity(
                operation = Rule.Operation.Exclude,
                directory = "/storage/emulated/0/Android",
                pattern = "{data,obb,data/**,obb/**}"
            ) // exclude private app data
        )
    }
}
