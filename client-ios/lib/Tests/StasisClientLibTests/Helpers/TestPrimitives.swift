import Foundation
import Synchronization

final class Counter: Sendable {
    private let storage = Mutex<Int>(0)
    func increment() { storage.withLock { $0 += 1 } }
    var value: Int { storage.withLock { $0 } }
}

final class Flag: Sendable {
    private let storage = Mutex<Bool>(false)
    func set() { storage.withLock { $0 = true } }
    var isSet: Bool { storage.withLock { $0 } }
}

struct TestFailure: Error, Equatable {
    let message: String

    init(message: String = "Test failure") {
        self.message = message
    }
}

final class Box<Value: Sendable>: Sendable {
    private let storage: Mutex<Value>

    init(_ initial: Value) {
        self.storage = Mutex(initial)
    }

    var value: Value { storage.withLock { $0 } }
    func set(_ new: Value) { storage.withLock { $0 = new } }
}

func waitUntil(
    timeout: TimeInterval = 5.0,
    interval: TimeInterval = 0.05,
    _ predicate: @Sendable () async -> Bool
) async {
    let deadline = Date().addingTimeInterval(timeout)
    while Date() < deadline {
        if await predicate() { return }
        try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
    }
}

final class StatsCounter<Stat>: Sendable where Stat: Hashable & Sendable & CaseIterable, Stat.AllCases: Sendable {
    private let stats: Mutex<[Stat: Int]>

    init() {
        self.stats = Mutex(Dictionary(uniqueKeysWithValues: Stat.allCases.map { ($0, 0) }))
    }

    var snapshot: [Stat: Int] { stats.withLock { $0 } }

    func increment(_ stat: Stat) {
        stats.withLock { $0[stat, default: 0] += 1 }
    }
}
