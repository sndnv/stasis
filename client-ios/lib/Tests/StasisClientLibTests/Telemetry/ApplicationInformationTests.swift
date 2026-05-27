@testable import StasisClientLib
import Testing

@Suite("ApplicationInformation")
struct ApplicationInformationTests {
    @Test("none ApplicationInformation provides no information")
    func noneProvidesNoInfo() {
        let none = NoApplicationInformation()
        #expect(none.name == "none")
        #expect(none.version == "none")
        #expect(none.buildTime == 0)
        #expect(none.asString() == "none;none;0")
    }

    @Test("valid ApplicationInformation provides information")
    func validProvidesInfo() {
        struct TestApp: ApplicationInformation {
            let name = "test-name"
            let version = "test-version"
            let buildTime: Int64 = 42
        }

        let app = TestApp()
        #expect(app.name == "test-name")
        #expect(app.version == "test-version")
        #expect(app.buildTime == 42)
        #expect(app.asString() == "test-name;test-version;42")
    }
}
