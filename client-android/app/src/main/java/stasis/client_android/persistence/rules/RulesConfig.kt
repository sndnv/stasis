package stasis.client_android.persistence.rules

import stasis.client_android.lib.collection.rules.Rule

object RulesConfig {
    val DefaultStorageDirectory: String = "/storage/emulated/0"

    val DefaultRules: List<RuleEntity> = listOf(
        RuleEntity(
            operation = Rule.Operation.Include,
            directory = DefaultStorageDirectory,
            pattern = "**",
            definition = null
        ), // includes all accessible user storage
        RuleEntity(
            operation = Rule.Operation.Exclude,
            directory = DefaultStorageDirectory,
            pattern = "{Android,Android/**}",
            definition = null
        ), // exclude private app data
        RuleEntity(
            operation = Rule.Operation.Exclude,
            directory = DefaultStorageDirectory,
            pattern = "**/.thumbnails",
            definition = null
        ) // exclude thumbnails
    )
}