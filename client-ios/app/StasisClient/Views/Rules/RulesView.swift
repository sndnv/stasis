import SwiftUI

struct RulesView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "Rules",
                systemImage: "text.badge.checkmark",
                description: Text("Coming soon")
            )
            .navigationTitle("Rules")
        }
    }
}

#Preview {
    RulesView()
}
