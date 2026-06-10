import SwiftUI

struct PlaceholderTabView: View {
    let title: String

    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                title,
                systemImage: "hourglass",
                description: Text("Coming soon")
            )
            .navigationTitle(title)
        }
    }
}

#Preview {
    PlaceholderTabView(title: "Backup")
}
