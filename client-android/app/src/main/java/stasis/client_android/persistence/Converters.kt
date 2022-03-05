package stasis.client_android.persistence

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.persistence.rules.RuleEntity
import stasis.client_android.persistence.schedules.ActiveScheduleEntity
import java.nio.file.Paths
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuidToString(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun stringToUuid(uuid: String?): UUID? = uuid?.let(UUID::fromString)

    @TypeConverter
    fun ruleOperationToString(operation: Rule.Operation?): String? =
        when (operation) {
            Rule.Operation.Include -> "include"
            Rule.Operation.Exclude -> "exclude"
            null -> null
        }

    @TypeConverter
    fun stringToRuleOperation(operation: String?): Rule.Operation? =
        when (operation) {
            "include" -> Rule.Operation.Include
            "exclude" -> Rule.Operation.Exclude
            null -> null
            else -> throw IllegalArgumentException("Unexpected operation provided: [$operation]")
        }

    companion object {
        fun Rule.asEntity(): RuleEntity = ruleToEntity(this)
        fun RuleEntity.asRule(): Rule = entityToRule(this)

        fun ActiveSchedule.asEntity(): ActiveScheduleEntity = activeScheduleToEntity(this)
        fun ActiveScheduleEntity.asActiveSchedule(): ActiveSchedule = entityToActiveSchedule(this)

        fun AccessTokenResponse.toJson(): String = accessTokenResponseToString(this)
        fun String.toAccessTokenResponse(): AccessTokenResponse = stringToAccessTokenResponse(this)

        fun ActiveSchedule.toJson(): String = activeScheduleToString(this)
        fun String.toActiveSchedule(): ActiveSchedule = stringToActiveSchedule(this)

        fun ruleToEntity(rule: Rule): RuleEntity =
            RuleEntity(
                id = rule.id,
                operation = rule.operation,
                directory = rule.directory,
                pattern = rule.pattern
            )

        fun entityToRule(entity: RuleEntity): Rule =
            Rule(
                id = entity.id,
                operation = entity.operation,
                directory = entity.directory,
                pattern = entity.pattern
            )

        fun activeScheduleToEntity(schedule: ActiveSchedule): ActiveScheduleEntity {
            val (type, data) = when (val assignment = schedule.assignment) {
                is OperationScheduleAssignment.Backup -> {
                    val json = JsonObject()
                    json.addProperty("definition", assignment.definition.toString())

                    json.add(
                        "entities",
                        JsonArray().apply { assignment.entities.forEach { add(it.toAbsolutePath().toString()) } }
                    )

                    "backup" to Gson().toJson(json)
                }
                is OperationScheduleAssignment.Expiration -> "expiration" to null
                is OperationScheduleAssignment.Validation -> "validation" to null
                is OperationScheduleAssignment.KeyRotation -> "key_rotation" to null
            }

            return ActiveScheduleEntity(
                id = 0,
                schedule = schedule.assignment.schedule,
                type = type,
                data = data
            )
        }

        fun entityToActiveSchedule(entity: ActiveScheduleEntity): ActiveSchedule {
            val assignment = when (entity.type) {
                "backup" -> {
                    val data = Gson().fromJson(entity.data, JsonObject::class.java)

                    OperationScheduleAssignment.Backup(
                        schedule = entity.schedule,
                        definition = UUID.fromString(data.get("definition").asString),
                        entities = data.get("entities").asJsonArray.map { Paths.get(it.asString) }
                    )
                }

                "expiration" -> OperationScheduleAssignment.Expiration(schedule = entity.schedule)
                "validation" -> OperationScheduleAssignment.Validation(schedule = entity.schedule)
                "key_rotation" -> OperationScheduleAssignment.KeyRotation(schedule = entity.schedule)
                else -> throw IllegalArgumentException("Unexpected entity type provided: [${entity.type}]")
            }

            return ActiveSchedule(id = entity.id, assignment = assignment)
        }

        fun activeScheduleToString(activeSchedule: ActiveSchedule): String =
            Gson().toJson(activeScheduleToJson(activeSchedule))

        fun stringToActiveSchedule(activeSchedule: String): ActiveSchedule =
            jsonToActiveSchedule(Gson().fromJson(activeSchedule, JsonObject::class.java))

        fun accessTokenResponseToString(response: AccessTokenResponse): String =
            Gson().toJson(accessTokenResponseToJson(response))

        fun stringToAccessTokenResponse(response: String): AccessTokenResponse =
            jsonToAccessTokenResponse(Gson().fromJson(response, JsonObject::class.java))

        private fun accessTokenResponseToJson(response: AccessTokenResponse): JsonObject {
            val json = JsonObject()
            json.addProperty("access_token", response.access_token)
            json.addProperty("refresh_token", response.refresh_token)
            json.addProperty("expires_in", response.expires_in)
            json.addProperty("scope", response.scope)

            return json
        }

        private fun jsonToAccessTokenResponse(response: JsonObject): AccessTokenResponse {
            return AccessTokenResponse(
                access_token = response.get("access_token").asString,
                refresh_token = response.get("refresh_token")?.asString,
                expires_in = response.get("expires_in").asLong,
                scope = response.get("scope")?.asString
            )
        }

        private fun activeScheduleToJson(activeSchedule: ActiveSchedule): JsonObject {
            val assignmentJson = JsonObject()
            assignmentJson.addProperty("schedule", activeSchedule.assignment.schedule.toString())

            when (val assignment = activeSchedule.assignment) {
                is OperationScheduleAssignment.Backup -> {
                    assignmentJson.addProperty("definition", assignment.definition.toString())

                    assignmentJson.add(
                        "entities",
                        JsonArray().apply { assignment.entities.forEach { add(it.toAbsolutePath().toString()) } }
                    )

                    assignmentJson.addProperty("type", "backup")
                }
                is OperationScheduleAssignment.Expiration -> assignmentJson.addProperty("type", "expiration")
                is OperationScheduleAssignment.Validation -> assignmentJson.addProperty("type", "validation")
                is OperationScheduleAssignment.KeyRotation -> assignmentJson.addProperty("type", "key_rotation")
            }

            val json = JsonObject()
            json.addProperty("id", activeSchedule.id)
            json.add("assignment", assignmentJson)

            return json
        }

        private fun jsonToActiveSchedule(activeSchedule: JsonObject): ActiveSchedule {
            val assignmentJson = activeSchedule.getAsJsonObject("assignment")
            val assignmentSchedule = UUID.fromString(assignmentJson.get("schedule").asString)
            val assignment = when (val assignmentType = assignmentJson.get("type").asString) {
                "backup" -> {
                    OperationScheduleAssignment.Backup(
                        schedule = assignmentSchedule,
                        definition = UUID.fromString(assignmentJson.get("definition").asString),
                        entities = assignmentJson.get("entities").asJsonArray.map { Paths.get(it.asString) }
                    )
                }

                "expiration" -> OperationScheduleAssignment.Expiration(schedule = assignmentSchedule)
                "validation" -> OperationScheduleAssignment.Validation(schedule = assignmentSchedule)
                "key_rotation" -> OperationScheduleAssignment.KeyRotation(schedule = assignmentSchedule)
                else -> throw IllegalArgumentException("Unexpected assignment type provided: [$assignmentType]")
            }

            return ActiveSchedule(
                id = activeSchedule.get("id").asLong,
                assignment = assignment
            )
        }
    }
}
