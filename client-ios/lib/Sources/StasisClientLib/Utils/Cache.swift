import Foundation

public protocol Cache<Key, Value>: Sendable {
    associatedtype Key: Hashable & Sendable
    associatedtype Value: Sendable

    /// Retrieves the cached value associated with `key`, if any.
    ///
    /// - Parameter key: the key associated with the needed value
    /// - Returns: the cached value or `nil` if none was found
    func get(_ key: Key) async -> Value?

    /// Puts a new `value` with the provided `key` in the cache.
    ///
    /// - Parameters:
    ///   - key: the key associated with the value
    ///   - value: the value to insert
    func put(_ key: Key, _ value: Value) async throws

    /// Puts the provided entries in the cache.
    ///
    /// - Parameter entries: entries to insert
    func put(entries: [Key: Value]) async throws

    /// Retrieves the cached value associated with `key` or attempts to load it.
    ///
    /// - Parameters:
    ///   - key: the key associated with the needed value
    ///   - load: function used for loading the value if it is not already in the cache
    /// - Returns: the cached or loaded value
    func getOrLoad(_ key: Key, load: @Sendable @escaping (Key) async throws -> Value?) async throws -> Value?

    /// Removes the cached value associated with `key`, if any.
    ///
    /// - Parameter key: the key associated with value to be removed
    func remove(_ key: Key) async throws

    /// Retrieves all cached values.
    func all() async -> [Key: Value]

    /// Removes all cached values.
    func clear() async throws

    /// Provides the current read statistics for this cache.
    var readStatistics: OperationStatistics { get async }

    /// Provides the current write statistics for this cache.
    var writeStatistics: OperationStatistics { get async }
}

public struct OperationStatistics: Sendable, Equatable, Hashable {
    public let bytesProcessed: Int64
    public let minDuration: Int64
    public let maxDuration: Int64
    public let operations: Int64

    public init(bytesProcessed: Int64, minDuration: Int64, maxDuration: Int64, operations: Int64) {
        self.bytesProcessed = bytesProcessed
        self.minDuration = minDuration
        self.maxDuration = maxDuration
        self.operations = operations
    }

    public static func empty() -> OperationStatistics {
        OperationStatistics(
            bytesProcessed: 0,
            minDuration: .max,
            maxDuration: .min,
            operations: 0
        )
    }

    public func updateWith(amount: Int, duration: Int64) -> OperationStatistics {
        OperationStatistics(
            bytesProcessed: bytesProcessed + Int64(amount),
            minDuration: duration > minDuration ? minDuration : duration,
            maxDuration: duration < maxDuration ? maxDuration : duration,
            operations: operations + 1
        )
    }
}

public protocol CacheSerdes<Key, Value>: Sendable {
    associatedtype Key
    associatedtype Value

    func encodeKey(_ key: Key) -> String
    func decodeKey(_ key: String) -> Key?
    func encodeValue(_ value: Value) throws -> Data
    func decodeValue(_ data: Data) throws -> Value
}

/// Simple in-memory cache backed by a `Dictionary` inside an actor.
public actor MapCache<Key: Hashable & Sendable, Value: Sendable>: Cache {
    private var storage: [Key: Value] = [:]

    public init() {}

    public nonisolated var readStatistics: OperationStatistics { .empty() }
    public nonisolated var writeStatistics: OperationStatistics { .empty() }

    public func get(_ key: Key) -> Value? {
        storage[key]
    }

    public func put(_ key: Key, _ value: Value) {
        storage[key] = value
    }

    public func put(entries: [Key: Value]) {
        storage.merge(entries) { _, new in new }
    }

    public func getOrLoad(
        _ key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?
    ) async throws -> Value? {
        if let cached = storage[key] { return cached }
        guard let loaded = try await load(key) else { return nil }
        storage[key] = loaded
        return loaded
    }

    public func remove(_ key: Key) {
        storage.removeValue(forKey: key)
    }

    public func all() -> [Key: Value] {
        storage
    }

    public func clear() {
        storage.removeAll()
    }
}

/// File-based cache.
///
/// - Parameters:
///   - directory: directory for storing state files
///   - serdes: key and value de/serializers
public actor FileCache<Key: Hashable & Sendable, Value: Sendable>: Cache {
    private let directory: URL
    private let serdes: any CacheSerdes<Key, Value>
    private var inMemory: [Key: Value] = [:]
    private var readStats: OperationStatistics = .empty()
    private var writeStats: OperationStatistics = .empty()

    public init(directory: URL, serdes: any CacheSerdes<Key, Value>) {
        self.directory = directory
        self.serdes = serdes
    }

    public var readStatistics: OperationStatistics { readStats }
    public var writeStatistics: OperationStatistics { writeStats }

    public func get(_ key: Key) -> Value? {
        if let cached = inMemory[key] { return cached }
        let url = fileURL(for: key)
        let start = DispatchTime.now()
        guard let data = try? Data(contentsOf: url) else { return nil }
        let duration = Self.millis(since: start)
        readStats = readStats.updateWith(amount: data.count, duration: duration)
        guard let decoded = try? serdes.decodeValue(data) else { return nil }
        inMemory[key] = decoded
        return decoded
    }

    public func put(_ key: Key, _ value: Value) throws {
        inMemory[key] = value
        let start = DispatchTime.now()
        let data = try serdes.encodeValue(value)
        ensureDirectory()
        try data.write(to: fileURL(for: key), options: .atomic)
        let duration = Self.millis(since: start)
        writeStats = writeStats.updateWith(amount: data.count, duration: duration)
    }

    public func put(entries: [Key: Value]) throws {
        for (key, value) in entries {
            try put(key, value)
        }
    }

    public func getOrLoad(
        _ key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?
    ) async throws -> Value? {
        if let cached = get(key) { return cached }
        guard let loaded = try await load(key) else { return nil }
        try put(key, loaded)
        return loaded
    }

    public func remove(_ key: Key) throws {
        inMemory.removeValue(forKey: key)
        let url = fileURL(for: key)
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }

    public func all() -> [Key: Value] {
        var result: [Key: Value] = [:]
        for key in listKeysOnDisk() {
            if let value = get(key) {
                result[key] = value
            }
        }
        return result
    }

    public func clear() throws {
        inMemory.removeAll()
        for key in listKeysOnDisk() {
            try remove(key)
        }
    }

    private static func millis(since start: DispatchTime) -> Int64 {
        Int64((DispatchTime.now().uptimeNanoseconds - start.uptimeNanoseconds) / 1_000_000)
    }

    private func ensureDirectory() {
        try? FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
    }

    private func fileURL(for key: Key) -> URL {
        directory.appendingPathComponent(serdes.encodeKey(key))
    }

    private func listKeysOnDisk() -> [Key] {
        guard let urls = try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: nil
        ) else { return [] }
        return urls.compactMap { serdes.decodeKey($0.lastPathComponent) }
    }
}

/// Time-based refreshing cache.
///
/// After `interval` has passed, values are reloaded using the `load` closure
/// originally associated with the key on the first `getOrLoad(_:load:)` call.
/// On failure, the next refresh attempt uses a reduced interval up to
/// `maxRefreshIntervalOnFailure`.
public actor RefreshingCache<Key: Hashable & Sendable, Value: Sendable>: Cache {
    public static var refreshIntervalOnFailureReduction: Int { 10 }
    public static var maxRefreshIntervalOnFailure: TimeInterval { 5 }

    public typealias RefreshListener = @Sendable (Key, Value?) async -> Void

    private let underlying: any Cache<Key, Value>
    private let interval: TimeInterval
    private var jobs: [Key: Task<Void, Never>] = [:]
    private var listeners: [UUID: RefreshListener] = [:]

    public init(underlying: any Cache<Key, Value>, interval: TimeInterval) {
        self.underlying = underlying
        self.interval = interval
    }

    public var readStatistics: OperationStatistics {
        get async { await underlying.readStatistics }
    }

    public var writeStatistics: OperationStatistics {
        get async { await underlying.writeStatistics }
    }

    public func get(_ key: Key) async -> Value? {
        await underlying.get(key)
    }

    public func put(_ key: Key, _ value: Value) async throws {
        try await underlying.put(key, value)
    }

    public func put(entries: [Key: Value]) async throws {
        try await underlying.put(entries: entries)
    }

    public func getOrLoad(
        _ key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?
    ) async throws -> Value? {
        let value = try await underlying.getOrLoad(key, load: load)
        if value != nil, jobs[key] == nil {
            scheduleRefresh(key: key, load: load, after: interval)
        }
        return value
    }

    public func remove(_ key: Key) async throws {
        jobs.removeValue(forKey: key)?.cancel()
        try await underlying.remove(key)
    }

    public func all() async -> [Key: Value] {
        await underlying.all()
    }

    public func clear() async throws {
        for job in jobs.values { job.cancel() }
        jobs.removeAll()
        try await underlying.clear()
    }

    @discardableResult
    public func register(onEntryRefreshed listener: @escaping RefreshListener) -> UUID {
        let id = UUID()
        listeners[id] = listener
        return id
    }

    public func unregister(_ id: UUID) {
        listeners.removeValue(forKey: id)
    }

    private func scheduleRefresh(
        key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?,
        after delay: TimeInterval
    ) {
        let task = Task<Void, Never> { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await self?.performRefresh(key: key, load: load)
        }
        jobs[key] = task
    }

    private func performRefresh(
        key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?
    ) async {
        let result: Result<Value?, any Error>
        do {
            result = .success(try await load(key))
        } catch {
            result = .failure(error)
        }
        guard !Task.isCancelled else { return }

        switch result {
        case .success(let refreshed):
            if let refreshed {
                try? await underlying.put(key, refreshed)
            } else {
                try? await underlying.remove(key)
            }
            await notify(key: key, value: refreshed)
            rescheduleIfActive(key: key, load: load, after: interval)
        case .failure:
            let nextDelay = min(
                interval / Double(Self.refreshIntervalOnFailureReduction),
                Self.maxRefreshIntervalOnFailure
            )
            rescheduleIfActive(key: key, load: load, after: nextDelay)
        }
    }

    private func rescheduleIfActive(
        key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?,
        after delay: TimeInterval
    ) {
        guard !Task.isCancelled else { return }
        scheduleRefresh(key: key, load: load, after: delay)
    }

    private func notify(key: Key, value: Value?) async {
        let snapshot = Array(listeners.values)
        for listener in snapshot {
            await listener(key, value)
        }
    }
}

/// Statistics-tracking cache.
///
/// Statistics about cache hits and misses are kept and provided.
///
/// - Parameter underlying: cache that handles the actual data storage
public actor TrackingCache<Key: Hashable & Sendable, Value: Sendable>: Cache {
    private let underlying: any Cache<Key, Value>
    private var hitCount: Int64 = 0
    private var missCount: Int64 = 0

    public init(underlying: any Cache<Key, Value>) {
        self.underlying = underlying
    }

    /// Provides the current number of cache hits.
    public var hits: Int64 { hitCount }

    /// Provides the current number of cache misses.
    public var misses: Int64 { missCount }

    public var readStatistics: OperationStatistics {
        get async { await underlying.readStatistics }
    }

    public var writeStatistics: OperationStatistics {
        get async { await underlying.writeStatistics }
    }

    public func get(_ key: Key) async -> Value? {
        let result = await underlying.get(key)
        if result == nil {
            missCount += 1
        } else {
            hitCount += 1
        }
        return result
    }

    public func put(_ key: Key, _ value: Value) async throws {
        try await underlying.put(key, value)
    }

    public func put(entries: [Key: Value]) async throws {
        try await underlying.put(entries: entries)
    }

    public func getOrLoad(
        _ key: Key,
        load: @Sendable @escaping (Key) async throws -> Value?
    ) async throws -> Value? {
        if let cached = await get(key) { return cached }
        guard let loaded = try await load(key) else { return nil }
        try await put(key, loaded)
        return loaded
    }

    public func remove(_ key: Key) async throws {
        try await underlying.remove(key)
    }

    public func all() async -> [Key: Value] {
        await underlying.all()
    }

    public func clear() async throws {
        try await underlying.clear()
    }
}
