import StasisClientLib
import SwiftUI

struct StatusView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: StatusModel?

    var body: some View {
        NavigationStack {
            Form {
                UserSection(user: model?.user, isLoading: model?.isLoading ?? true)
                DeviceSection(device: model?.device, isLoading: model?.isLoading ?? true)
                ConnectionsSection(servers: model?.servers ?? [:])
            }
            .navigationTitle("Status")
            .refreshable { await model?.refresh() }
            .alert("Error", isPresented: errorBinding) {
                Button("OK") { model?.clearError() }
            } message: {
                Text(model?.error ?? "")
            }
            .task { await loadAndObserve() }
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { model?.error != nil },
            set: { if !$0 { model?.clearError() } }
        )
    }

    private func loadAndObserve() async {
        if model == nil, let session = container.session {
            model = StatusModel(session: session, trackers: container.trackers)
        }
        await model?.start()
    }
}

#Preview {
    StatusView()
        .environment(AppContainer())
}
