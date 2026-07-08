import Foundation
import Gzip
import StasisSharedProto

public enum DatasetMetadataError: Error, Equatable, LocalizedError {
    case decompressionFailed(String)
    case decodingFailed(String)

    public var errorDescription: String? {
        switch self {
        case .decompressionFailed(let reason):
            "Failed to decompress dataset metadata: \(reason)"
        case .decodingFailed(let reason):
            "Failed to decode dataset metadata: \(reason)"
        }
    }
}

extension DatasetMetadata {
    public func toByteString() throws -> Data {
        var proto = Stasis_ClientIos_Lib_Model_Proto_DatasetMetadata()
        proto.contentChanged = contentChanged.mapValues { $0.proto }
        proto.metadataChanged = metadataChanged.mapValues { $0.proto }
        proto.filesystem = filesystem.proto
        let serialized = try proto.serializedData()
        return try serialized.gzipped()
    }

    public init(byteString: Data) throws {
        let decompressed: Data
        do {
            decompressed = try byteString.gunzipped()
        } catch {
            throw DatasetMetadataError.decompressionFailed(String(describing: error))
        }

        let proto: Stasis_ClientIos_Lib_Model_Proto_DatasetMetadata
        do {
            proto = try Stasis_ClientIos_Lib_Model_Proto_DatasetMetadata(serializedBytes: decompressed)
        } catch {
            throw DatasetMetadataError.decodingFailed(String(describing: error))
        }

        let contentChanged = try proto.contentChanged.mapValues { try EntityMetadata(proto: $0) }
        let metadataChanged = try proto.metadataChanged.mapValues { try EntityMetadata(proto: $0) }
        let filesystem = try FilesystemMetadata(proto: proto.filesystem)
        self.init(
            contentChanged: contentChanged,
            metadataChanged: metadataChanged,
            filesystem: filesystem
        )
    }
}
