import SwiftUI

struct SubmittingOverlay: ViewModifier {
    let isSubmitting: Bool

    func body(content: Content) -> some View {
        content.overlay {
            if isSubmitting {
                ProgressView()
                    .controlSize(.large)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.ultraThinMaterial)
            }
        }
    }
}

extension View {
    func submittingOverlay(_ isSubmitting: Bool) -> some View {
        modifier(SubmittingOverlay(isSubmitting: isSubmitting))
    }
}
