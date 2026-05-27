import Foundation
@testable import StasisClientLib
import Testing

private let key = "test-key"
private let value = "test-initial-value"

private enum CacheTestError: Error, Equatable {
    case testFailure
    case decode
}

private struct StringStringSerdes: CacheSerdes {
    func encodeKey(_ key: String) -> String { key }
    func decodeKey(_ key: String) -> String? { key }
    func encodeValue(_ value: String) throws -> Data { Data(value.utf8) }
    func decodeValue(_ data: Data) throws -> String {
        guard let string = String(data: data, encoding: .utf8) else {
            throw CacheTestError.decode
        }
        return string
    }
}

private let stringSerdes = StringStringSerdes()

private func makeTempDir() throws -> URL {
    let dir = FileManager.default.temporaryDirectory
        .appendingPathComponent("stasis-cache-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir
}

private actor LoadCounter {
    private(set) var count: Int = 0
    func increment() { count += 1 }
}

@Suite("Cache OperationStatistics")
struct OperationStatisticsTests {
    @Test("supports updating")
    func supportsUpdating() {
        let empty = OperationStatistics.empty()

        #expect(empty.bytesProcessed == 0)
        #expect(empty.minDuration == .max)
        #expect(empty.maxDuration == .min)
        #expect(empty.operations == 0)

        let firstUpdate = empty.updateWith(amount: 50, duration: 100)

        #expect(firstUpdate.bytesProcessed == 50)
        #expect(firstUpdate.minDuration == 100)
        #expect(firstUpdate.maxDuration == 100)
        #expect(firstUpdate.operations == 1)

        let secondUpdate = firstUpdate.updateWith(amount: 30, duration: 150)

        #expect(secondUpdate.bytesProcessed == 80)
        #expect(secondUpdate.minDuration == 100)
        #expect(secondUpdate.maxDuration == 150)
        #expect(secondUpdate.operations == 2)

        let thirdUpdate = secondUpdate.updateWith(amount: 30, duration: 50)

        #expect(thirdUpdate.bytesProcessed == 110)
        #expect(thirdUpdate.minDuration == 50)
        #expect(thirdUpdate.maxDuration == 150)
        #expect(thirdUpdate.operations == 3)
    }
}

@Suite("Map Cache")
struct MapCacheTests {
    @Test("supports caching data")
    func supportsCachingData() async throws {
        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            return value
        }

        let cache = MapCache<String, String>()

        #expect(cache.readStatistics.bytesProcessed == 0)
        #expect(cache.writeStatistics.bytesProcessed == 0)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)

        #expect(await loadedValues.count == 1)

        #expect(cache.readStatistics.bytesProcessed == 0)
        #expect(cache.writeStatistics.bytesProcessed == 0)
    }

    @Test("supports explicitly adding data (individual)")
    func supportsAddingIndividual() async throws {
        let cache = MapCache<String, String>()

        #expect(await cache.get(key) == nil)
        await cache.put(key, value)
        #expect(await cache.get(key) == value)
    }

    @Test("supports explicitly adding data (bulk)")
    func supportsAddingBulk() async throws {
        let cache = MapCache<String, String>()

        let key1 = "test-key-1"
        let key2 = "test-key-2"
        let key3 = "test-key-3"

        #expect(await cache.get(key1) == nil)
        #expect(await cache.get(key2) == nil)
        #expect(await cache.get(key3) == nil)
        await cache.put(entries: [key1: value, key2: value, key3: value])
        #expect(await cache.get(key1) == value)
        #expect(await cache.get(key2) == value)
        #expect(await cache.get(key3) == value)
    }

    @Test("supports removing data")
    func supportsRemovingData() async throws {
        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            return value
        }

        let cache = MapCache<String, String>()

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)

        await cache.remove(key)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)

        #expect(await loadedValues.count == 2)
    }

    @Test("does not update the cache if the load operation fails")
    func loadFailureDoesNotUpdate() async throws {
        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            throw CacheTestError.testFailure
        }

        let cache = MapCache<String, String>()

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        await #expect(throws: CacheTestError.testFailure) {
            try await cache.getOrLoad(key, load: load)
        }

        #expect(await loadedValues.count == 1)
        #expect(await cache.get(key) == nil)
    }

    @Test("supports retrieving all cached data")
    func supportsRetrievingAll() async throws {
        let cache = MapCache<String, String>()

        await cache.put("k1", "v1")
        await cache.put("k2", "v2")
        await cache.put("k3", "v3")
        await cache.put("k4", "v4")
        await cache.put("k5", "v5")

        let expected: [String: String] = [
            "k1": "v1",
            "k2": "v2",
            "k3": "v3",
            "k4": "v4",
            "k5": "v5",
        ]

        #expect(await cache.all() == expected)
    }

    @Test("supports clearing all cached data")
    func supportsClearing() async throws {
        let cache = MapCache<String, String>()

        await cache.put("k1", "v1")
        await cache.put("k2", "v2")
        await cache.put("k3", "v3")

        #expect(await cache.all().count == 3)

        await cache.clear()

        #expect(await cache.all().isEmpty)
    }
}

@Suite("File Cache")
struct FileCacheTests {
    @Test("supports caching data")
    func supportsCachingData() async throws {
        let target = try makeTempDir()

        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            return value
        }

        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 0)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)

        #expect(await loadedValues.count == 1)

        let files = try FileManager.default.contentsOfDirectory(
            at: target,
            includingPropertiesForKeys: nil
        )
        let cachedPath = try #require(files.first)
        let content = try Data(contentsOf: cachedPath)
        #expect(try stringSerdes.decodeValue(content) == value)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == Int64(Data(value.utf8).count))
    }

    @Test("supports reading cached data from storage")
    func supportsReadingFromStorage() async throws {
        let target = try makeTempDir()

        let primaryCache = FileCache<String, String>(directory: target, serdes: stringSerdes)
        let secondaryCache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        #expect(await primaryCache.readStatistics.bytesProcessed == 0)
        #expect(await primaryCache.writeStatistics.bytesProcessed == 0)
        #expect(await secondaryCache.readStatistics.bytesProcessed == 0)
        #expect(await secondaryCache.writeStatistics.bytesProcessed == 0)

        #expect(await primaryCache.get(key) == nil)
        #expect(await secondaryCache.get(key) == nil)

        try await primaryCache.put(key, value)
        #expect(await secondaryCache.get(key) == value)

        let valueBytes = Int64(Data(value.utf8).count)
        #expect(await primaryCache.readStatistics.bytesProcessed == 0)
        #expect(await primaryCache.writeStatistics.bytesProcessed == valueBytes)
        #expect(await secondaryCache.readStatistics.bytesProcessed == valueBytes)
        #expect(await secondaryCache.writeStatistics.bytesProcessed == 0)
    }

    @Test("supports explicitly adding data (individual)")
    func supportsAddingIndividual() async throws {
        let target = try makeTempDir()
        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 0)

        #expect(await cache.get(key) == nil)
        try await cache.put(key, value)
        #expect(await cache.get(key) == value)

        let files = try FileManager.default.contentsOfDirectory(
            at: target,
            includingPropertiesForKeys: nil
        )
        let cachedPath = try #require(files.first)
        let content = try Data(contentsOf: cachedPath)
        #expect(try stringSerdes.decodeValue(content) == value)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == Int64(Data(value.utf8).count))
    }

    @Test("supports explicitly adding data (bulk)")
    func supportsAddingBulk() async throws {
        let target = try makeTempDir()
        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        let key1 = "test-key-1"
        let key2 = "test-key-2"
        let key3 = "test-key-3"

        #expect(await cache.get(key1) == nil)
        #expect(await cache.get(key2) == nil)
        #expect(await cache.get(key3) == nil)
        try await cache.put(entries: [key1: value, key2: value, key3: value])
        #expect(await cache.get(key1) == value)
        #expect(await cache.get(key2) == value)
        #expect(await cache.get(key3) == value)

        let cachedPaths = try FileManager.default
            .contentsOfDirectory(at: target, includingPropertiesForKeys: nil)
            .filter { $0.lastPathComponent.hasPrefix("test-key") }
        #expect(cachedPaths.count == 3)

        for cachedPath in cachedPaths {
            let content = try Data(contentsOf: cachedPath)
            #expect(try stringSerdes.decodeValue(content) == value)
        }

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == Int64(3 * Data(value.utf8).count))
    }

    @Test("supports removing data")
    func supportsRemovingData() async throws {
        let target = try makeTempDir()

        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            return value
        }

        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)

        try await cache.remove(key)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)

        #expect(await loadedValues.count == 2)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == Int64(2 * Data(value.utf8).count))
    }

    @Test("does not update the cache if the load operation fails")
    func loadFailureDoesNotUpdate() async throws {
        let target = try makeTempDir()

        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            throw CacheTestError.testFailure
        }

        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        await #expect(throws: CacheTestError.testFailure) {
            try await cache.getOrLoad(key, load: load)
        }

        #expect(await loadedValues.count == 1)
        #expect(await cache.get(key) == nil)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 0)
    }

    @Test("supports retrieving all cached data")
    func supportsRetrievingAll() async throws {
        let target = try makeTempDir()
        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        try await cache.put("k1", "v1")
        try await cache.put("k2", "v2")
        try await cache.put("k3", "v3")
        try await cache.put("k4", "v4")
        try await cache.put("k5", "v5")

        let expected: [String: String] = [
            "k1": "v1",
            "k2": "v2",
            "k3": "v3",
            "k4": "v4",
            "k5": "v5",
        ]

        #expect(await cache.all() == expected)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 10)
    }

    @Test("supports clearing all cached data")
    func supportsClearing() async throws {
        let target = try makeTempDir()
        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        try await cache.put("k1", "v1")
        try await cache.put("k2", "v2")
        try await cache.put("k3", "v3")

        #expect(await cache.all().count == 3)

        try await cache.clear()

        #expect(await cache.all().isEmpty)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 6)
    }

    @Test("handles failures when retrieving all cache entries")
    func handlesAllFailures() async throws {
        let target = FileManager.default.temporaryDirectory
            .appendingPathComponent("stasis-cache-missing-\(UUID().uuidString)")
        let cache = FileCache<String, String>(directory: target, serdes: stringSerdes)

        #expect(await cache.all().isEmpty)
    }
}

@Suite("Tracking Cache")
struct TrackingCacheTests {
    @Test("supports caching data")
    func supportsCachingData() async throws {
        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            return value
        }

        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 0)

        #expect(await cache.hits == 0)
        #expect(await cache.misses == 0)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        #expect(await cache.hits == 0)
        #expect(await cache.misses == 1)

        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)

        #expect(await cache.hits == 2)
        #expect(await cache.misses == 2)

        #expect(await loadedValues.count == 1)

        #expect(await cache.readStatistics.bytesProcessed == 0)
        #expect(await cache.writeStatistics.bytesProcessed == 0)
    }

    @Test("supports explicitly adding data (individual)")
    func supportsAddingIndividual() async throws {
        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        #expect(await cache.hits == 0)
        #expect(await cache.misses == 0)

        #expect(await cache.get(key) == nil)

        #expect(await cache.hits == 0)
        #expect(await cache.misses == 1)

        try await cache.put(key, value)
        #expect(await cache.get(key) == value)

        #expect(await cache.hits == 1)
        #expect(await cache.misses == 1)
    }

    @Test("supports explicitly adding data (bulk)")
    func supportsAddingBulk() async throws {
        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        let key1 = "test-key-1"
        let key2 = "test-key-2"
        let key3 = "test-key-3"

        #expect(await cache.hits == 0)
        #expect(await cache.misses == 0)

        #expect(await cache.get(key1) == nil)
        #expect(await cache.get(key2) == nil)
        #expect(await cache.get(key3) == nil)

        #expect(await cache.hits == 0)
        #expect(await cache.misses == 3)

        try await cache.put(entries: [key1: value, key2: value, key3: value])
        #expect(await cache.get(key1) == value)
        #expect(await cache.get(key2) == value)
        #expect(await cache.get(key3) == value)

        #expect(await cache.hits == 3)
        #expect(await cache.misses == 3)
    }

    @Test("supports removing data")
    func supportsRemovingData() async throws {
        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            return value
        }

        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)

        try await cache.remove(key)
        #expect(await cache.get(key) == nil)

        #expect(try await cache.getOrLoad(key, load: load) == value)
        #expect(try await cache.getOrLoad(key, load: load) == value)

        #expect(await loadedValues.count == 2)
    }

    @Test("does not update the cache if the load operation fails")
    func loadFailureDoesNotUpdate() async throws {
        let loadedValues = LoadCounter()
        let load: @Sendable (String) async throws -> String? = { _ in
            await loadedValues.increment()
            throw CacheTestError.testFailure
        }

        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        #expect(await loadedValues.count == 0)
        #expect(await cache.get(key) == nil)

        await #expect(throws: CacheTestError.testFailure) {
            try await cache.getOrLoad(key, load: load)
        }

        #expect(await loadedValues.count == 1)
        #expect(await cache.get(key) == nil)
    }

    @Test("supports retrieving all cached data")
    func supportsRetrievingAll() async throws {
        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        try await cache.put("k1", "v1")
        try await cache.put("k2", "v2")
        try await cache.put("k3", "v3")
        try await cache.put("k4", "v4")
        try await cache.put("k5", "v5")

        let expected: [String: String] = [
            "k1": "v1",
            "k2": "v2",
            "k3": "v3",
            "k4": "v4",
            "k5": "v5",
        ]

        #expect(await cache.all() == expected)
    }

    @Test("supports clearing all cached data")
    func supportsClearing() async throws {
        let underlying = MapCache<String, String>()
        let cache = TrackingCache<String, String>(underlying: underlying)

        try await cache.put("k1", "v1")
        try await cache.put("k2", "v2")
        try await cache.put("k3", "v3")

        #expect(await cache.all().count == 3)

        try await cache.clear()

        #expect(await cache.all().isEmpty)
    }
}
