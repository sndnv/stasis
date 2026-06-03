import Foundation
@testable import StasisClientLib
import Testing

@Suite("StateStore")
struct StateStoreTests {
    private struct Record: Equatable, Sendable {
        let a: String
        let b: Int
        let c: Bool
    }

    private struct RecordSerdes: StateStoreSerdes {
        func serialize(_ state: [String: Record]) throws -> Data {
            let joined = state
                .map { (key, value) in "\(key)->\(value.a),\(value.b),\(value.c)" }
                .joined(separator: ";")
            return Data(joined.utf8)
        }

        func deserialize(_ bytes: Data) throws -> [String: Record] {
            guard let raw = String(bytes: bytes, encoding: .utf8) else {
                throw RecordSerdesError.invalidUtf8
            }
            return try raw
                .split(separator: ";")
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .reduce(into: [:]) { acc, entry in
                    let keyValue = entry.split(separator: "->", maxSplits: 1).map(String.init)
                    guard keyValue.count == 2 else { throw RecordSerdesError.malformed }
                    let fields = keyValue[1].split(separator: ",").map(String.init)
                    guard fields.count == 3,
                          let b = Int(fields[1]),
                          let c = Bool(fields[2]) else { throw RecordSerdesError.malformed }
                    acc[keyValue[0]] = Record(a: fields[0], b: b, c: c)
                }
        }
    }

    private enum RecordSerdesError: Error, Equatable {
        case malformed
        case invalidUtf8
    }

    private func makeTarget() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("statestore-test-\(UUID().uuidString)")
    }

    private func filesIn(_ target: URL) throws -> [URL] {
        try FileManager.default
            .contentsOfDirectory(at: target, includingPropertiesForKeys: nil)
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    @Test("persists state to file")
    func persistsStateToFile() async throws {
        let target = makeTarget()
        defer { try? FileManager.default.removeItem(at: target) }
        let serdes = RecordSerdes()
        let store = try StateStore<[String: Record]>(target: target, retainedVersions: 10, serdes: serdes)

        let state: [String: Record] = [
            "id-a": Record(a: "a", b: 1, c: true),
            "id-b": Record(a: "b", b: 2, c: false),
            "id-c": Record(a: "c", b: 3, c: true)
        ]

        try await store.persist(state)

        let persisted = try filesIn(target)
        #expect(persisted.count == 1)

        let content = try Data(contentsOf: persisted[0])
        let deserialized = try serdes.deserialize(content)
        #expect(deserialized == state)
    }

    @Test("prunes old state files")
    func prunesOldStateFiles() async throws {
        let target = makeTarget()
        defer { try? FileManager.default.removeItem(at: target) }
        let serdes = RecordSerdes()
        let store = try StateStore<[String: Record]>(target: target, retainedVersions: 10, serdes: serdes)

        let initialState: [String: Record] = [:]
        let updatedState: [String: Record] = ["id-a": Record(a: "a", b: 1, c: true)]
        let latestState: [String: Record] = [
            "id-a": Record(a: "a", b: 1, c: true),
            "id-b": Record(a: "b", b: 2, c: false),
            "id-c": Record(a: "c", b: 3, c: true)
        ]

        try await store.persist(initialState)
        try await Task.sleep(for: .milliseconds(50))
        try await store.persist(updatedState)
        try await Task.sleep(for: .milliseconds(50))
        try await store.persist(latestState)
        try await Task.sleep(for: .milliseconds(50))

        let beforePruning = try filesIn(target)
        let stateBeforePruning = try beforePruning.map { try serdes.deserialize(Data(contentsOf: $0)) }

        try await store.prune(keep: 1)

        let afterPruning = try filesIn(target)
        let stateAfterPruning = try afterPruning.map { try serdes.deserialize(Data(contentsOf: $0)) }

        #expect(beforePruning.count == 3)
        #expect(afterPruning.count == 1)

        #expect(stateBeforePruning.count == 3)
        #expect(stateBeforePruning[0] == initialState)
        #expect(stateBeforePruning[1] == updatedState)
        #expect(stateBeforePruning[2] == latestState)

        #expect(stateAfterPruning.count == 1)
        #expect(stateAfterPruning[0] == latestState)
    }

    @Test("discards state files")
    func discardsStateFiles() async throws {
        let target = makeTarget()
        defer { try? FileManager.default.removeItem(at: target) }
        let serdes = RecordSerdes()
        let store = try StateStore<[String: Record]>(target: target, retainedVersions: 10, serdes: serdes)

        let initialState: [String: Record] = [:]
        let updatedState: [String: Record] = ["id-a": Record(a: "a", b: 1, c: true)]
        let latestState: [String: Record] = [
            "id-a": Record(a: "a", b: 1, c: true),
            "id-b": Record(a: "b", b: 2, c: false),
            "id-c": Record(a: "c", b: 3, c: true)
        ]

        try await store.persist(initialState)
        try await Task.sleep(for: .milliseconds(50))
        try await store.persist(updatedState)
        try await Task.sleep(for: .milliseconds(50))
        try await store.persist(latestState)
        try await Task.sleep(for: .milliseconds(50))

        let beforeDiscard = try filesIn(target)
        let stateBeforeDiscard = try beforeDiscard.map { try serdes.deserialize(Data(contentsOf: $0)) }

        try await store.discard()

        let afterDiscard = try filesIn(target)

        #expect(beforeDiscard.count == 3)
        #expect(afterDiscard.isEmpty)

        #expect(stateBeforeDiscard.count == 3)
        #expect(stateBeforeDiscard[0] == initialState)
        #expect(stateBeforeDiscard[1] == updatedState)
        #expect(stateBeforeDiscard[2] == latestState)
    }

    @Test("restores existing state from file")
    func restoresExistingStateFromFile() async throws {
        let target = makeTarget()
        defer { try? FileManager.default.removeItem(at: target) }
        let serdes = RecordSerdes()
        let store = try StateStore<[String: Record]>(target: target, retainedVersions: 10, serdes: serdes)

        let state: [String: Record] = [
            "id-a": Record(a: "a", b: 1, c: true),
            "id-b": Record(a: "b", b: 2, c: false),
            "id-c": Record(a: "c", b: 3, c: true)
        ]

        try await store.persist(state)

        let restored = try await store.restore()
        #expect(restored == state)
    }

    @Test("handles deserialization failures")
    func handlesDeserializationFailures() async throws {
        let target = makeTarget()
        defer { try? FileManager.default.removeItem(at: target) }
        let serdes = RecordSerdes()
        let store = try StateStore<[String: Record]>(target: target, retainedVersions: 10, serdes: serdes)

        let initialState: [String: Record] = [:]
        let updatedState: [String: Record] = ["id-a": Record(a: "a", b: 1, c: true)]
        let latestState: [String: Record] = [
            "id-a": Record(a: "a", b: 1, c: true),
            "id-b": Record(a: "b", b: 2, c: false),
            "id-c": Record(a: "c", b: 3, c: true)
        ]

        try await store.persist(initialState)
        try await Task.sleep(for: .milliseconds(50))
        try await store.persist(updatedState)
        try await Task.sleep(for: .milliseconds(50))
        try await store.persist(latestState)
        try await Task.sleep(for: .milliseconds(50))

        let persisted = try filesIn(target)
        if let newest = persisted.last {
            try Data("invalid".utf8).write(to: newest)
        }
        let restoredAfterCorruptingNewest = try await store.restore()
        #expect(restoredAfterCorruptingNewest == updatedState)

        for file in try filesIn(target) {
            try Data("invalid".utf8).write(to: file)
        }
        let restoredAfterCorruptingAll = try await store.restore()
        #expect(restoredAfterCorruptingAll == nil)
    }

    @Test("supports the convenience init with the default retained-versions")
    func supportsConvenienceInit() async throws {
        let target = makeTarget()
        defer { try? FileManager.default.removeItem(at: target) }
        let serdes = RecordSerdes()
        let store = try StateStore<[String: Record]>(target: target, serdes: serdes)

        for index in 0..<5 {
            try await store.persist(["id-\(index)": Record(a: "a", b: index, c: true)])
            try await Task.sleep(for: .milliseconds(2))
        }

        let persisted = try filesIn(target)
        #expect(persisted.count == StateStore<[String: Record]>.minRetainedVersions)
    }
}
