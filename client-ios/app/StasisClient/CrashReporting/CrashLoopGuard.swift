import Foundation

struct CrashLoopGuard {
    let store: UserDefaults
    let threshold: Int

    func registerLaunch() -> Bool {
        let prior = store.crashLoopAttempts()
        store.setCrashLoopAttempts(prior + 1)
        return prior >= threshold
    }

    func reset() {
        store.setCrashLoopAttempts(0)
    }
}
