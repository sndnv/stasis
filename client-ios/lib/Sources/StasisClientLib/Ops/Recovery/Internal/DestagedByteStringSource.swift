import Foundation

public enum DestagedByteStringSource {
    public static func destage(
        _ source: AsyncThrowingStream<Data, Error>,
        to: URL,
        providers: RecoveryProviders
    ) async throws {
        let staged = try await providers.staging.temporary()
        do {
            if !FileManager.default.fileExists(atPath: staged.path) {
                FileManager.default.createFile(atPath: staged.path, contents: nil)
            }
            let handle = try FileHandle(forWritingTo: staged)
            do {
                for try await chunk in source {
                    try handle.write(contentsOf: chunk)
                }
                try handle.close()
            } catch {
                try? handle.close()
                throw error
            }
            try await providers.staging.destage(from: staged, to: to)
        } catch {
            try? await providers.staging.discard(file: staged)
            throw error
        }
    }
}
