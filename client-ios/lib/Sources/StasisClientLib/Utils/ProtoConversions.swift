import Foundation

extension Date {
    var epochMillis: UInt64 { UInt64(timeIntervalSince1970 * 1000) }

    init(epochMillis: UInt64) {
        self.init(timeIntervalSince1970: TimeInterval(epochMillis) / 1000)
    }
}

extension Dictionary where Key == URL {
    func keyedByPath() -> [String: Value] {
        [String: Value](uniqueKeysWithValues: map { ($0.key.path, $0.value) })
    }
}

extension Dictionary where Key == String {
    func keyedByFileURL() -> [URL: Value] {
        [URL: Value](uniqueKeysWithValues: map { (URL(fileURLWithPath: $0.key), $0.value) })
    }
}
