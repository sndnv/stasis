import Foundation
import Observation
import StasisClientLib

@Observable
@MainActor
final class AppContainer {
    let libVersion: String = StasisClientLib.version
}
