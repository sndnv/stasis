import Foundation
@testable import StasisClientLib

struct MockCompression: Compression {
    let defaultCompression: any Compressor
    let disabledExtensions: Set<String>

    init(defaultCompression: any Compressor = Identity.shared, disabledExtensions: Set<String> = ["test"]) {
        self.defaultCompression = defaultCompression
        self.disabledExtensions = disabledExtensions
    }
}
