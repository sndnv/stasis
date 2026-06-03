import Foundation
@testable import StasisClientLib
import Testing

@Suite("MergedCrates")
struct MergedCratesTests {
    @Test("merges a single crate")
    func mergeSingleCrate() async throws {
        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") { makeDataStream(Data("original_1".utf8)) }
        ]
        let partsProcessed = Counter()

        let merged = try MergedCrates.merge(crates) { partsProcessed.increment() }
        var collected = Data()
        for try await chunk in merged { collected.append(chunk) }

        #expect(partsProcessed.value == 1)
        #expect(String(data: collected, encoding: .utf8) == "original_1")
    }

    @Test("merges multiple crates in part-id order")
    func mergeMultipleCrates() async throws {
        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") { makeDataStream(Data("original_1".utf8)) },
            RecoveryCrate(partId: 2, partPath: "/tmp/file/one__part=2") { makeDataStream(Data("original_3".utf8)) },
            RecoveryCrate(partId: 1, partPath: "/tmp/file/one__part=1") { makeDataStream(Data("original_2".utf8)) }
        ]
        let partsProcessed = Counter()

        let merged = try MergedCrates.merge(crates) { partsProcessed.increment() }
        var collected = Data()
        for try await chunk in merged { collected.append(chunk) }

        #expect(partsProcessed.value == 3)
        #expect(String(data: collected, encoding: .utf8) == "original_1original_2original_3")
    }

    @Test("throws when no crates are provided")
    func failsWithoutCrates() {
        let partsProcessed = Counter()
        #expect(throws: MergedCratesError.noCrates) {
            _ = try MergedCrates.merge([]) { partsProcessed.increment() }
        }
        #expect(partsProcessed.value == 0)
    }

    @Test("propagates failures from the underlying crate source")
    func propagatesSourceFailures() async {
        let partsProcessed = Counter()
        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") { makeDataStream(Data("first".utf8)) },
            RecoveryCrate(partId: 1, partPath: "/tmp/file/one__part=1") { throw TestFailure(message: "boom") }
        ]

        let merged = try! MergedCrates.merge(crates) { partsProcessed.increment() }

        var collected = Data()
        await #expect(throws: TestFailure(message: "boom")) {
            for try await chunk in merged { collected.append(chunk) }
        }

        #expect(String(data: collected, encoding: .utf8) == "first")
        #expect(partsProcessed.value == 1)
    }

    @Test("cancels the producing task when the consumer drops the stream")
    func cancellationPropagates() async {
        let started = Counter()
        let cancelled = Flag()

        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") {
                started.increment()
                return AsyncThrowingStream { continuation in
                    let task = Task {
                        while !Task.isCancelled {
                            try? await Task.sleep(for: .milliseconds(10))
                        }
                        cancelled.set()
                        continuation.finish()
                    }
                    continuation.onTermination = { _ in task.cancel() }
                }
            }
        ]

        let merged = try! MergedCrates.merge(crates) { }
        var iter = merged.makeAsyncIterator()
        let consumer = Task {
            _ = try? await iter.next()
        }
        try? await Task.sleep(for: .milliseconds(20))
        consumer.cancel()

        let deadline = Date().addingTimeInterval(2.0)
        while !cancelled.isSet && Date() < deadline {
            try? await Task.sleep(for: .milliseconds(20))
        }

        #expect(started.value == 1)
        #expect(cancelled.isSet)
    }
}
