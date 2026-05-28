import Foundation

public struct DefaultServerApiEndpointClient: ServerApiEndpointClient, ApiClient {
    public let selfDevice: DeviceId
    public let server: String
    public let http: HttpClient
    public let decryption: DecryptionContext

    public init(
        serverApiUrl: String,
        credentialsProvider: any HttpCredentialsProvider,
        decryption: DecryptionContext,
        selfDevice: DeviceId,
        retryConfig: RetryConfig = .default
    ) {
        self.selfDevice = selfDevice
        self.server = serverApiUrl.trimmedTrailingSlash
        self.decryption = decryption
        self.http = HttpClient(
            credentialsProvider: credentialsProvider,
            retryConfig: retryConfig
        )
    }

    init(serverApiUrl: String, selfDevice: DeviceId, http: HttpClient, decryption: DecryptionContext = .disabled) {
        self.selfDevice = selfDevice
        self.server = serverApiUrl.trimmedTrailingSlash
        self.decryption = decryption
        self.http = http
    }

    public func datasetDefinitions() async throws -> [DatasetDefinition] {
        let request = makeRequest("/v1/datasets/definitions/own", method: "GET")
        let all: [DatasetDefinition] = try await jsonListRequest(request)
        return all.filter { $0.device == selfDevice }
    }

    public func datasetDefinition(definition: DatasetDefinitionId) async throws -> DatasetDefinition {
        let request = makeRequest("/v1/datasets/definitions/own/\(definition.uuidString.lowercased())", method: "GET")
        let actual: DatasetDefinition = try await jsonRequest(request)
        guard actual.device == selfDevice else {
            throw EndpointFailure(message: "Cannot retrieve dataset definition for a different device")
        }
        return actual
    }

    public func createDatasetDefinition(request: CreateDatasetDefinition) async throws -> CreatedDatasetDefinition {
        guard request.device == selfDevice else {
            throw EndpointFailure(
                message: "Cannot create dataset definition for a different device: [\(request.device.uuidString.lowercased())]"
            )
        }
        var urlRequest = makeRequest("/v1/datasets/definitions/own", method: "POST")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try encodeBody(request)
        return try await jsonRequest(urlRequest)
    }

    public func updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition) async throws {
        var urlRequest = makeRequest("/v1/datasets/definitions/own/\(definition.uuidString.lowercased())", method: "PUT")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try encodeBody(request)
        try await emptyRequest(urlRequest)
    }

    public func deleteDatasetDefinition(definition: DatasetDefinitionId) async throws {
        let urlRequest = makeRequest("/v1/datasets/definitions/own/\(definition.uuidString.lowercased())", method: "DELETE")
        try await emptyRequest(urlRequest)
    }

    public func datasetEntries(definition: DatasetDefinitionId) async throws -> [DatasetEntry] {
        let urlRequest = makeRequest("/v1/datasets/entries/own/for-definition/\(definition.uuidString.lowercased())", method: "GET")
        return try await jsonListRequest(urlRequest)
    }

    public func datasetEntry(entry: DatasetEntryId) async throws -> DatasetEntry {
        let urlRequest = makeRequest("/v1/datasets/entries/own/\(entry.uuidString.lowercased())", method: "GET")
        return try await jsonRequest(urlRequest)
    }

    public func latestEntry(definition: DatasetDefinitionId, until: Date?) async throws -> DatasetEntry? {
        let basePath = "/v1/datasets/entries/own/for-definition/\(definition.uuidString.lowercased())/latest"
        let path: String
        if let until {
            path = "\(basePath)?until=\(formatInstant(until))"
        } else {
            path = basePath
        }
        let urlRequest = makeRequest(path, method: "GET")
        do {
            return try await jsonRequest(urlRequest)
        } catch is ResourceMissingFailure {
            return nil
        }
    }

    public func createDatasetEntry(request: CreateDatasetEntry) async throws -> CreatedDatasetEntry {
        let definitionId = request.definition.uuidString.lowercased()
        var urlRequest = makeRequest(
            "/v1/datasets/entries/own/for-definition/\(definitionId)",
            method: "POST"
        )
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try encodeBody(request)
        return try await jsonRequest(urlRequest)
    }

    public func deleteDatasetEntry(entry: DatasetEntryId) async throws {
        let urlRequest = makeRequest("/v1/datasets/entries/own/\(entry.uuidString.lowercased())", method: "DELETE")
        try await emptyRequest(urlRequest)
    }

    public func publicSchedules() async throws -> [Schedule] {
        let urlRequest = makeRequest("/v1/schedules/public", method: "GET")
        return try await jsonListRequest(urlRequest)
    }

    public func publicSchedule(schedule: ScheduleId) async throws -> Schedule {
        let urlRequest = makeRequest("/v1/schedules/public/\(schedule.uuidString.lowercased())", method: "GET")
        return try await jsonRequest(urlRequest)
    }

    public func datasetMetadata(entry: DatasetEntryId) async throws -> DatasetMetadata {
        let resolved = try await datasetEntry(entry: entry)
        return try await datasetMetadata(entry: resolved)
    }

    public func datasetMetadata(entry: DatasetEntry) async throws -> DatasetMetadata {
        switch decryption {
        case .disabled:
            throw EndpointFailure(message: "Cannot retrieve dataset metadata; decryption context is disabled")
        case .enabled(let core, let deviceSecretProvider):
            guard let encrypted = try await core.pull(crate: entry.metadata) else {
                throw EndpointFailure(
                    message: "Cannot decrypt metadata crate [\(entry.metadata.uuidString.lowercased())];" +
                        " no data provided"
                )
            }
            let metadataSecret = deviceSecretProvider().toMetadataSecret(metadataCrate: entry.metadata)
            let decrypted = try metadataSecret.decrypt(encrypted)
            return try DatasetMetadata(byteString: decrypted)
        }
    }

    public func user() async throws -> User {
        let urlRequest = makeRequest("/v1/users/self", method: "GET")
        return try await jsonRequest(urlRequest)
    }

    public func resetUserSalt() async throws -> UpdatedUserSalt {
        var urlRequest = makeRequest("/v1/users/self/salt", method: "PUT")
        urlRequest.httpBody = Data()
        return try await jsonRequest(urlRequest)
    }

    public func resetUserPassword(request: ResetUserPassword) async throws {
        var urlRequest = makeRequest("/v1/users/self/password", method: "PUT")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try encodeBody(request)
        try await emptyRequest(urlRequest)
    }

    public func device() async throws -> Device {
        let urlRequest = makeRequest("/v1/devices/own/\(selfDevice.uuidString.lowercased())", method: "GET")
        return try await jsonRequest(urlRequest)
    }

    public func pushDeviceKey(key: Data) async throws {
        var urlRequest = makeRequest("/v1/devices/own/\(selfDevice.uuidString.lowercased())/key", method: "PUT")
        urlRequest.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = key
        try await emptyRequest(urlRequest)
    }

    public func pullDeviceKey() async throws -> Data {
        let urlRequest = makeRequest("/v1/devices/own/\(selfDevice.uuidString.lowercased())/key", method: "GET")
        let (data, response) = try await http.send(urlRequest)
        try response.successful()
        guard !data.isEmpty else { throw ResourceMissingFailure() }
        return data
    }

    public func deviceKeyExists() async throws -> Bool {
        let urlRequest = makeRequest("/v1/devices/own/\(selfDevice.uuidString.lowercased())/key", method: "HEAD")
        do {
            try await emptyRequest(urlRequest)
            return true
        } catch is ResourceMissingFailure {
            return false
        }
    }

    public func ping() async throws -> Ping {
        let urlRequest = makeRequest("/v1/service/ping", method: "GET")
        return try await jsonRequest(urlRequest)
    }

    public func commands(lastSequenceId: Int64?) async throws -> [CommandAsJson] {
        let basePath = "/v1/devices/own/\(selfDevice.uuidString.lowercased())/commands"
        let path: String
        if let lastSequenceId {
            path = "\(basePath)?last_sequence_id=\(lastSequenceId)"
        } else {
            path = basePath
        }
        let urlRequest = makeRequest(path, method: "GET")
        return try await jsonListRequest(urlRequest)
    }

    public func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
        var urlRequest = makeRequest("/v1/analytics", method: "POST")
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try encodeBody(CreateAnalyticsEntry(entry: entry.asJson()))
        let _: CreatedAnalyticsEntry = try await jsonRequest(urlRequest)
    }

    private func makeRequest(_ path: String, method: String) -> URLRequest {
        var request = URLRequest(url: URL(string: "\(server)\(path)")!)
        request.httpMethod = method
        return request
    }

    private func formatInstant(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }
}
