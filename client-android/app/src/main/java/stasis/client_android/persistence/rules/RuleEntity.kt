package stasis.client_android.persistence.rules

import androidx.room.Entity
import androidx.room.PrimaryKey
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val operation: Rule.Operation,
    val directory: String,
    val pattern: String,
    val definition: DatasetDefinitionId?
)
