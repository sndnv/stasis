import Foundation

public enum MergedCrates {
    public static func merge(
        _ crates: [RecoveryCrate],
        onPartProcessed: @escaping @Sendable () async -> Void
    ) throws -> AsyncThrowingStream<Data, Error> {
        guard !crates.isEmpty else {
            throw MergedCratesError.noCrates
        }

        let sorted = crates.sorted { $0.partId < $1.partId }

        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for crate in sorted {
                        let stream = try await crate.source()
                        for try await chunk in stream {
                            continuation.yield(chunk)
                        }
                        await onPartProcessed()
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

public enum MergedCratesError: Error, Equatable, LocalizedError {
    case noCrates

    public var errorDescription: String? {
        switch self {
        case .noCrates:
            "No crates were available to merge"
        }
    }
}
