import Foundation

extension ConfigRepository.Keys {
    enum CrashReporting {
        static let launchAttempts = "crash_reporting_launch_attempts"
        static let lastSummary = "crash_reporting_last_summary"
    }
}

extension UserDefaults {
    func crashLoopAttempts() -> Int {
        integer(forKey: ConfigRepository.Keys.CrashReporting.launchAttempts)
    }

    func setCrashLoopAttempts(_ value: Int) {
        set(value, forKey: ConfigRepository.Keys.CrashReporting.launchAttempts)
    }

    func lastCrashSummary() -> String? {
        string(forKey: ConfigRepository.Keys.CrashReporting.lastSummary)
    }

    func setLastCrashSummary(_ value: String) {
        set(value, forKey: ConfigRepository.Keys.CrashReporting.lastSummary)
    }
}
