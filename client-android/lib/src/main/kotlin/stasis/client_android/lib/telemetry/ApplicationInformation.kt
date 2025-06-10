package stasis.client_android.lib.telemetry

interface ApplicationInformation {
    val name: String
    val version: String
    val buildTime: Long

    fun asString(): String = "$name;$version;$buildTime"

    companion object {
        fun none(): ApplicationInformation = object : ApplicationInformation {
            override val name: String = "none"
            override val version: String = "none"
            override val buildTime: Long = 0L
        }
    }
}
