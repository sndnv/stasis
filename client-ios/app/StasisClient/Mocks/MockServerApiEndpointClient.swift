#if DEBUG
import Foundation
import StasisClientLib

actor MockServerApiEndpointClient: ServerApiEndpointClient {
    nonisolated let selfDevice: DeviceId = MockConfig.device
    nonisolated let server: String = MockConfig.serverApi

    private let maxSimulatedDelay: TimeInterval

    init(maxSimulatedDelay: TimeInterval = 2.0) {
        self.maxSimulatedDelay = maxSimulatedDelay
    }

    private func sleep() async {
        let delay = TimeInterval.random(in: 0...maxSimulatedDelay)
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
    }

    func datasetDefinitions() async throws -> [DatasetDefinition] {
        await sleep()
        let extra = DatasetDefinition(
            id: UUID(), info: Self.otherDefinition.info, device: Self.otherDefinition.device,
            redundantCopies: Self.otherDefinition.redundantCopies,
            existingVersions: Self.otherDefinition.existingVersions,
            removedVersions: Self.otherDefinition.removedVersions,
            created: Date(), updated: Date()
        )
        return [Self.defaultDefinition, Self.otherDefinition, extra]
    }

    func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        await sleep()
        if definition == Self.defaultDefinition.id { return Self.defaultDefinition }
        throw MockError.invalidDefinition(definition)
    }

    func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition {
        await sleep()
        return CreatedDatasetDefinition(definition: Self.defaultDefinition.id)
    }

    func updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition) async throws {
        await sleep()
    }

    func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws {
        await sleep()
    }

    func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry] {
        await sleep()
        guard definition == Self.defaultDefinition.id else { throw MockError.invalidDefinition(definition) }
        var entries: [DatasetEntry] = [Self.defaultEntry, Self.extraEntry]
        for _ in 0..<20 {
            entries.append(DatasetEntry(
                id: UUID(),
                definition: Self.defaultDefinition.id,
                device: selfDevice,
                data: [UUID(), UUID()],
                metadata: UUID(),
                changes: Int64.random(in: 1...100),
                size: Int64.random(in: 1024...(1024 * 1024 * 500)),
                created: Date().addingTimeInterval(-TimeInterval.random(in: 60...(86_400 * 30)))
            ))
        }
        return entries
    }

    func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry {
        await sleep()
        if entry == Self.defaultEntry.id { return Self.defaultEntry }
        if entry == Self.extraEntry.id { return Self.extraEntry }
        throw MockError.invalidEntry(entry)
    }

    func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? {
        await sleep()
        return definition == Self.defaultDefinition.id ? Self.defaultEntry : nil
    }

    func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        await sleep()
        return CreatedDatasetEntry(entry: Self.defaultEntry.id)
    }

    func deleteDatasetEntry(entry: DatasetEntryId) async throws {
        await sleep()
    }

    func publicSchedules() async throws -> [Schedule] {
        await sleep()
        return [Self.defaultSchedule, Self.secondSchedule]
    }

    func publicSchedule(schedule: ScheduleId) async throws -> Schedule {
        await sleep()
        if schedule == Self.defaultSchedule.id { return Self.defaultSchedule }
        if schedule == Self.secondSchedule.id { return Self.secondSchedule }
        throw MockError.invalidSchedule(schedule)
    }

    func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata {
        await sleep()
        return entry == Self.extraEntry.id ? Self.extraMetadata : Self.defaultMetadata
    }

    func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata {
        try await datasetMetadata(entry: entry.id)
    }

    func user() async throws -> User {
        await sleep()
        return Self.currentUser
    }

    func resetUserSalt() async throws -> UpdatedUserSalt {
        await sleep()
        return UpdatedUserSalt(salt: Self.currentUser.salt)
    }

    func resetUserPassword(request: ResetUserPassword) async throws {
        await sleep()
    }

    func device() async throws -> Device {
        await sleep()
        return Self.currentDevice
    }

    func pushDeviceKey(key: Data) async throws {
        await sleep()
    }

    func pullDeviceKey() async throws -> Data {
        await sleep()
        throw ResourceMissingFailure()
    }

    func deviceKeyExists() async throws -> Bool {
        await sleep()
        return false
    }

    func ping() async throws -> Ping {
        await sleep()
        return Ping(id: UUID())
    }

    func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] {
        await sleep()
        let all: [CommandAsJson] = [
            CommandAsJson(
                sequenceId: 1, source: "user", target: selfDevice,
                parameters: .init(logoutUser: .init(reason: "Test Logout")),
                created: Date()
            ),
            CommandAsJson(
                sequenceId: 2, source: "service", target: nil,
                parameters: .init(logoutUser: nil),
                created: Date()
            )
        ]
        return all.filter { $0.sequenceId > (lastSequenceId ?? 0) }
    }

    func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
        await sleep()
    }

    enum MockError: Error, LocalizedError {
        case invalidDefinition(DatasetDefinitionId)
        case invalidEntry(DatasetEntryId)
        case invalidSchedule(ScheduleId)

        var errorDescription: String? {
            switch self {
            case .invalidDefinition(let id):
                "Unknown dataset definition [\(id.uuidString)]"
            case .invalidEntry(let id):
                "Unknown dataset entry [\(id.uuidString)]"
            case .invalidSchedule(let id):
                "Unknown schedule [\(id.uuidString)]"
            }
        }
    }

    static func pastDate(minDays: Double, maxDays: Double) -> Date {
        Date().addingTimeInterval(-TimeInterval.random(in: (86_400 * minDays)...(86_400 * maxDays)))
    }

    static func pastDates() -> (created: Date, updated: Date) {
        let created = pastDate(minDays: 7, maxDays: 365)
        let updated = min(created.addingTimeInterval(TimeInterval.random(in: 0...(86_400 * 7))), Date())
        return (created, updated)
    }

    static let defaultDefinition: DatasetDefinition = {
        let dates = pastDates()
        return DatasetDefinition(
            id: UUID(),
            info: "test-definition",
            device: MockConfig.device,
            redundantCopies: 42,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3_600 * 12)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3_600 * 366)),
            created: dates.created,
            updated: dates.updated
        )
    }()

    static let otherDefinition: DatasetDefinition = {
        let dates = pastDates()
        return DatasetDefinition(
            id: UUID(),
            info: "other-definition",
            device: MockConfig.device,
            redundantCopies: 2,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3_600 * 12)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3_600 * 366)),
            created: dates.created,
            updated: dates.updated
        )
    }()

    static let defaultEntry = DatasetEntry(
        id: UUID(),
        definition: defaultDefinition.id,
        device: MockConfig.device,
        data: [UUID(), UUID()],
        metadata: UUID(),
        changes: 1,
        size: 2,
        created: pastDate(minDays: 1, maxDays: 60)
    )

    static let extraEntry = DatasetEntry(
        id: UUID(),
        definition: defaultDefinition.id,
        device: MockConfig.device,
        data: [UUID()],
        metadata: UUID(),
        changes: nil,
        size: 3,
        created: pastDate(minDays: 1, maxDays: 60)
    )

    static let defaultSchedule = Schedule(
        id: UUID(),
        info: "test-schedule-1",
        isPublic: true,
        start: LocalDateTime(ISO8601DateFormatter().string(from: Date().addingTimeInterval(3_600 * 4))),
        interval: SecondsDuration(3_600 * 12),
        created: Date().addingTimeInterval(-42),
        updated: Date()
    )

    static let secondSchedule = Schedule(
        id: UUID(),
        info: "test-schedule-2",
        isPublic: true,
        start: LocalDateTime(ISO8601DateFormatter().string(from: Date().addingTimeInterval(3_600 * 6))),
        interval: SecondsDuration(3_600 * 12),
        created: Date().addingTimeInterval(-42),
        updated: Date()
    )

    private static let fileOnePath = "/tmp/file/one"
    private static let fileTwoPath = "/tmp/file/.two"
    private static let fileFourPath = "/tmp/other/four"

    static let defaultMetadata = makeMetadata(generatedPrefix: "cc")
    static let extraMetadata = makeMetadata(generatedPrefix: "fs")

    private static func makeMetadata(generatedPrefix: String) -> DatasetMetadata {
        let entityCount = 50
        return DatasetMetadata(
            contentChanged: makeContentChanged(generatedPrefix: generatedPrefix, count: entityCount),
            metadataChanged: makeMetadataChanged(),
            filesystem: makeFilesystem(generatedPrefix: generatedPrefix, count: entityCount)
        )
    }

    private static func makeContentChanged(generatedPrefix: String, count: Int) -> [String: EntityMetadata] {
        let baseCrate = UUID(uuidString: "329efbeb-80a3-42b8-b1dc-79bc0fea7bca")!
        let one = pastDates()
        var result: [String: EntityMetadata] = [
            fileOnePath: .file(.init(
                path: fileOnePath, link: nil, isHidden: false,
                created: one.created, updated: one.updated,
                owner: "root", group: "root", permissions: "rwxrwxrwx",
                size: 1, checksum: Data([1]),
                crates: ["/tmp/file/one_0": baseCrate],
                compression: "none"
            ))
        ]
        result.merge(MockLibraryFixtures.contentEntities) { current, _ in current }
        for index in 0..<count {
            let path = "/tmp/file/generated_\(generatedPrefix)_\(index)"
            let dates = pastDates()
            result[path] = .file(.init(
                path: path, link: nil, isHidden: false,
                created: dates.created, updated: dates.updated,
                owner: "root", group: "root", permissions: "rwxrwxrwx",
                size: Int64(index), checksum: Data([1]),
                crates: ["\(path)_0": baseCrate],
                compression: "none"
            ))
        }
        return result
    }

    private static func makeMetadataChanged() -> [String: EntityMetadata] {
        let dates = pastDates()
        let extraCrate = UUID(uuidString: "e672a956-1a95-4304-8af0-9418f0e43cba")!
        return [
            fileTwoPath: .file(.init(
                path: fileTwoPath, link: "/tmp/file/three", isHidden: false,
                created: dates.created, updated: dates.updated,
                owner: "root", group: "root", permissions: "rwxrwxrwx",
                size: 2, checksum: Data([42]),
                crates: ["\(fileTwoPath)_0": extraCrate],
                compression: "gzip"
            ))
        ]
    }

    private static func makeFilesystem(generatedPrefix: String, count: Int) -> FilesystemMetadata {
        var entities: [String: FilesystemMetadata.EntityState] = [
            fileOnePath: .new,
            fileTwoPath: .updated,
            fileFourPath: .existing(entry: extraEntry.id)
        ]
        entities.merge(MockLibraryFixtures.filesystemStates) { current, _ in current }
        for index in 0..<count {
            entities["/tmp/file/generated_\(generatedPrefix)_lu_\(index)"] = .existing(entry: extraEntry.id)
        }
        return FilesystemMetadata(entities: entities)
    }

    static let currentUser: User = {
        let oneTib: Int64 = 1_099_511_627_776
        let sixtyFourGib: Int64 = 68_719_476_736
        let limits = User.Limits(
            maxDevices: 12 * 1024,
            maxCrates: .max,
            maxStorage: oneTib,
            maxStoragePerCrate: sixtyFourGib,
            maxRetention: SecondsDuration(86_400 * 4),
            minRetention: SecondsDuration(3_600 * 12)
        )
        let dates = pastDates()
        return User(
            id: MockConfig.user,
            salt: "test-salt",
            active: true,
            limits: limits,
            permissions: [
                "manage-service", "view-public", "view-service",
                "view-privileged", "view-self", "manage-privileged", "manage-self"
            ],
            created: dates.created,
            updated: dates.updated
        )
    }()

    static let currentDevice: Device = {
        let dates = pastDates()
        return Device(
            id: MockConfig.device,
            name: "test-device",
            node: MockConfig.deviceNode,
            owner: MockConfig.user,
            active: true,
            limits: nil,
            created: dates.created,
            updated: dates.updated
        )
    }()
}
#endif
