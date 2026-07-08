import Foundation
@testable import StasisClient
import Testing

@Suite struct CrashLoopGuardTests {
    @Test func registerLaunchIncrementsTheCounter() {
        let store = TestDefaults.isolatedDefaults()

        let sut = CrashLoopGuard(store: store, threshold: 3)
        _ = sut.registerLaunch()
        _ = sut.registerLaunch()

        #expect(store.crashLoopAttempts() == 2)
    }

    @Test func tripsOnlyAfterThresholdConsecutiveLaunches() {
        let store = TestDefaults.isolatedDefaults()

        let sut = CrashLoopGuard(store: store, threshold: 3)

        #expect(sut.registerLaunch() == false)
        #expect(sut.registerLaunch() == false)
        #expect(sut.registerLaunch() == false)
        #expect(sut.registerLaunch() == true)
    }

    @Test func resetClearsTheCounter() {
        let store = TestDefaults.isolatedDefaults()

        let sut = CrashLoopGuard(store: store, threshold: 2)
        _ = sut.registerLaunch()
        _ = sut.registerLaunch()
        _ = sut.registerLaunch()
        #expect(sut.registerLaunch() == true)

        sut.reset()

        #expect(store.crashLoopAttempts() == 0)
        #expect(sut.registerLaunch() == false)
    }
}
