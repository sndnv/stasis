import Foundation

public enum DestagedByteStringSource {
    public static func destage(
        _ source: AsyncThrowingStream<Data, Error>,
        to: URL,
        providers: RecoveryProviders
    ) async throws {
        let staged = try await providers.staging.temporary()
        do {
            var collected = Data()
            for try await chunk in source {
                collected.append(chunk)
            }
            try collected.write(to: staged)
            try await providers.staging.destage(from: staged, to: to)
        } catch {
            try? await providers.staging.discard(file: staged)
            throw error
        }
    }
}
