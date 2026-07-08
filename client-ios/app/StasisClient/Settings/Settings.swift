import Foundation

public enum Settings {
    public enum DateTimeFormat: String, Sendable, CaseIterable {
        case system
        case iso
    }

    public enum Keys {
        public static let dateTimeFormat = "date_time_format"
        public static let manageUserCredentialsUpdatePassword = "manage_user_credentials_update_password"
        public static let manageUserCredentialsUpdateSalt = "manage_user_credentials_update_salt"
        public static let manageDeviceSecretRemotelyPush = "manage_device_secret_remotely_push"
        public static let manageDeviceSecretRemotelyPull = "manage_device_secret_remotely_pull"
        public static let manageDeviceSecretLocallyExport = "manage_device_secret_locally_export"
        public static let manageDeviceSecretLocallyImport = "manage_device_secret_locally_import"
        public static let schedulingEnabled = "scheduling_enabled"
        public static let pingInterval = "ping_interval"
        public static let commandRefreshInterval = "command_refresh_interval"
        public static let showAvailableCommands = "show_available_commands"
        public static let showSupportedCommands = "show_supported_commands"
        public static let discoveryInterval = "discovery_interval"
        public static let cacheActiveInterval = "cache_active_interval"
        public static let cachePendingInterval = "cache_pending_interval"
        public static let showCacheStatistics = "show_cache_statistics"
        public static let analyticsEnabled = "analytics_enabled"
        public static let analyticsKeepEvents = "analytics_keep_events"
        public static let analyticsKeepFailures = "analytics_keep_failures"
        public static let analyticsPersistenceInterval = "analytics_persistence_interval"
        public static let analyticsTransmissionInterval = "analytics_transmission_interval"
        public static let analyticsShowCollected = "analytics_show_collected"
        public static let showPermissions = "show_permissions"
        public static let resetConfig = "reset_config"
    }

    public enum Defaults {
        public static let dateTimeFormat: DateTimeFormat = .system
        public static let schedulingEnabled = true
        public static let pingInterval: TimeInterval = 3 * 60
        public static let commandRefreshInterval: TimeInterval = 5 * 60
        public static let discoveryInterval: TimeInterval = 30 * 60
        public static let cacheActiveInterval: TimeInterval = 30
        public static let cachePendingInterval: TimeInterval = 60 * 60
        public static let analyticsEnabled = true
        public static let analyticsKeepEvents = true
        public static let analyticsKeepFailures = true
        public static let analyticsPersistenceInterval: TimeInterval = 5 * 60
        public static let analyticsTransmissionInterval: TimeInterval = 30 * 60
    }

    public static func parseDateTimeFormat(_ format: String) throws -> DateTimeFormat {
        guard let result = DateTimeFormat(rawValue: format) else {
            throw SettingsError.unexpectedDateTimeFormat(format)
        }
        return result
    }
}

public enum SettingsError: Error, Equatable, LocalizedError {
    case unexpectedDateTimeFormat(String)

    public var errorDescription: String? {
        switch self {
        case .unexpectedDateTimeFormat(let value):
            "Unexpected date/time format [\(value)]"
        }
    }
}

public extension UserDefaults {
    func dateTimeFormat() -> Settings.DateTimeFormat {
        guard let raw = string(forKey: Settings.Keys.dateTimeFormat),
              let parsed = try? Settings.parseDateTimeFormat(raw) else {
            return Settings.Defaults.dateTimeFormat
        }
        return parsed
    }

    func schedulingEnabled() -> Bool {
        value(forKey: Settings.Keys.schedulingEnabled) as? Bool
            ?? Settings.Defaults.schedulingEnabled
    }

    func pingInterval() -> TimeInterval {
        seconds(forKey: Settings.Keys.pingInterval, default: Settings.Defaults.pingInterval)
    }

    func commandRefreshInterval() -> TimeInterval {
        seconds(forKey: Settings.Keys.commandRefreshInterval, default: Settings.Defaults.commandRefreshInterval)
    }

    func discoveryInterval() -> TimeInterval {
        seconds(forKey: Settings.Keys.discoveryInterval, default: Settings.Defaults.discoveryInterval)
    }

    func cacheActiveInterval() -> TimeInterval {
        seconds(forKey: Settings.Keys.cacheActiveInterval, default: Settings.Defaults.cacheActiveInterval)
    }

    func cachePendingInterval() -> TimeInterval {
        seconds(forKey: Settings.Keys.cachePendingInterval, default: Settings.Defaults.cachePendingInterval)
    }

    func analyticsEnabled() -> Bool {
        value(forKey: Settings.Keys.analyticsEnabled) as? Bool ?? Settings.Defaults.analyticsEnabled
    }

    func analyticsKeepEvents() -> Bool {
        value(forKey: Settings.Keys.analyticsKeepEvents) as? Bool ?? Settings.Defaults.analyticsKeepEvents
    }

    func analyticsKeepFailures() -> Bool {
        value(forKey: Settings.Keys.analyticsKeepFailures) as? Bool ?? Settings.Defaults.analyticsKeepFailures
    }

    func analyticsPersistenceInterval() -> TimeInterval {
        seconds(
            forKey: Settings.Keys.analyticsPersistenceInterval,
            default: Settings.Defaults.analyticsPersistenceInterval
        )
    }

    func analyticsTransmissionInterval() -> TimeInterval {
        seconds(
            forKey: Settings.Keys.analyticsTransmissionInterval,
            default: Settings.Defaults.analyticsTransmissionInterval
        )
    }

    private func seconds(forKey key: String, default fallback: TimeInterval) -> TimeInterval {
        switch object(forKey: key) {
        case let value as Double: return value
        case let value as Int: return TimeInterval(value)
        case let raw as String: return Int64(raw).map(TimeInterval.init) ?? fallback
        default: return fallback
        }
    }
}
