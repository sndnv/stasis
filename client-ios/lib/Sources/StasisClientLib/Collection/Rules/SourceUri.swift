import Foundation
import fsi

public enum SourceUri {
    public static func scheme(_ source: String) -> String? {
        Schemes.extract(source).scheme
    }
}
