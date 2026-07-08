import SwiftUI

struct HelpButton: View {
    let topic: HelpTopic

    @State private var showing = false

    var body: some View {
        Button {
            showing = true
        } label: {
            Image(systemName: "questionmark.circle")
        }
        .accessibilityLabel("Help")
        .sheet(isPresented: $showing) {
            HelpSheet(topic: topic)
        }
    }
}
