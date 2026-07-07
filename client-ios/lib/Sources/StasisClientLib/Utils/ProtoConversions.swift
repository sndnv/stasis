import Foundation

extension Date {
    var epochMillis: UInt64 { UInt64(timeIntervalSince1970 * 1000) }

    init(epochMillis: UInt64) {
        self.init(timeIntervalSince1970: TimeInterval(epochMillis) / 1000)
    }
}

extension Dictionary where Key == EntityRef {
    func keyedByKey() -> [String: Value] {
        [String: Value](uniqueKeysWithValues: map { ($0.key.key, $0.value) })
    }
}

extension Dictionary where Key == String {
    func keyedByRef() -> [EntityRef: Value] {
        [EntityRef: Value](uniqueKeysWithValues: map { (EntityRef.default(key: $0.key), $0.value) })
    }
}
