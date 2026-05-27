@testable import StasisClientLib
import Testing

@Test func libraryExposesVersion() {
    #expect(StasisClientLib.version == "0.0.0")
}
