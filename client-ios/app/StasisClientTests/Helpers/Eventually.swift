import Foundation
import Testing

func eventually(
    timeout: Duration = .seconds(10),
    interval: Duration = .milliseconds(50),
    _ check: () async -> Bool
) async {
    let deadline = ContinuousClock.now.advanced(by: timeout)
    while ContinuousClock.now < deadline {
        if await check() { return }
        try? await Task.sleep(for: interval)
    }
    Issue.record("eventually condition did not become true within \(timeout)")
}
