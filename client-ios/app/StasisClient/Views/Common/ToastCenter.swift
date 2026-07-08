import SwiftUI

@MainActor
@Observable
final class ToastCenter {
    private(set) var current: Toast?
    private var dismissTask: Task<Void, Never>?
    private let displayDuration: Duration

    init(displayDuration: Duration) {
        self.displayDuration = displayDuration
    }

    func show(_ message: String) {
        dismissTask?.cancel()
        current = Toast(message: message)
        let duration = displayDuration
        dismissTask = Task { [weak self] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled else { return }
            self?.current = nil
        }
    }

    func dismiss() {
        dismissTask?.cancel()
        current = nil
    }
}
