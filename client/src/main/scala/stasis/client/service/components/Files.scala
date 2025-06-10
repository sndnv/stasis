package stasis.client.service.components

object Files {
  final val ConfigOverride: String = "client.conf"
  final val DeviceSecret: String = "device-secret"
  final val ApiToken: String = "api-token"
  final val CommandState: String = "command-state"
  final val AnalyticsCache: String = "analytics-cache.json"

  object Templates {
    final val ConfigOverride: String = "client.conf.template"
    final val RulesLinux: String = "client.rules.linux.template"
    final val RulesMacOS: String = "client.rules.macos.template"
  }

  object TrustStores {
    final val Authentication: String = "authentication"
    final val ServerApi: String = "server-api"
    final val ServerCore: String = "server-core"
  }

  object KeyStores {
    final val ClientApi: String = "client-api"
  }

  object Default {
    final val ClientRules: String = "client.rules"
    final val ClientSchedules: String = "client.schedules"
  }
}
