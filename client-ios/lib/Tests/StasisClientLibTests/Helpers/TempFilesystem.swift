import Darwin
import Foundation

final class TempFilesystem: Sendable {
    let root: URL

    init(prefix: String = "stasis-tests") throws {
        let manager = FileManager.default
        let directory = manager.temporaryDirectory.appendingPathComponent("\(prefix)-\(UUID().uuidString)", isDirectory: true)
        try manager.createDirectory(at: directory, withIntermediateDirectories: true)

        var buffer = [CChar](repeating: 0, count: Int(PATH_MAX))
        guard realpath(directory.path, &buffer) != nil else {
            throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno))
        }
        let nullIndex = buffer.firstIndex(of: 0) ?? buffer.count
        let bytes = buffer[..<nullIndex].map { UInt8(bitPattern: $0) }
        guard let path = String(bytes: bytes, encoding: .utf8) else {
            throw NSError(domain: NSPOSIXErrorDomain, code: Int(EILSEQ))
        }
        self.root = URL(fileURLWithPath: path, isDirectory: true)
    }

    deinit {
        try? FileManager.default.removeItem(at: root)
    }

    func resolve(_ relative: String) -> URL {
        root.appendingPathComponent(relative)
    }

    @discardableResult
    func createFile(_ relative: String, contents: Data = Data()) throws -> URL {
        let url = resolve(relative)
        let parent = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true)
        guard FileManager.default.createFile(atPath: url.path, contents: contents) else {
            throw NSError(domain: NSCocoaErrorDomain, code: NSFileWriteUnknownError, userInfo: [
                NSFilePathErrorKey: url.path
            ])
        }
        return url
    }

    @discardableResult
    func createDirectory(_ relative: String) throws -> URL {
        let url = resolve(relative)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    func exists(_ relative: String) -> Bool {
        FileManager.default.fileExists(atPath: resolve(relative).path)
    }
}
