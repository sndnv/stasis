@testable import StasisClient
import Testing

@MainActor
@Suite("ToastCenter")
struct ToastCenterTests {
    @Test("show sets the current toast")
    func showSetsCurrent() {
        let center = ToastCenter(displayDuration: .seconds(60))
        #expect(center.current == nil)
        center.show("test")
        #expect(center.current?.message == "test")
    }

    @Test("show replaces the current toast")
    func showReplaces() {
        let center = ToastCenter(displayDuration: .seconds(60))
        center.show("test a")
        let first = center.current
        center.show("test b")
        #expect(center.current?.message == "test b")
        #expect(center.current != first)
    }

    @Test("dismiss clears the current toast")
    func dismissClears() {
        let center = ToastCenter(displayDuration: .seconds(60))
        center.show("test")
        center.dismiss()
        #expect(center.current == nil)
    }
}
