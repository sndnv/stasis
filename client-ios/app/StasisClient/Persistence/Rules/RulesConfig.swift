import Foundation
import StasisClientLib

public enum RulesConfig {
    public static let defaultRules: [Rule] = [
        Rule(
            id: 1,
            operation: .include,
            source: "\(PhotoAlbumSelector.scheme):/",
            pattern: "*",
            definition: nil
        )
    ]
}
