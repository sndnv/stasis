import StasisClientLib
import SwiftUI

struct HomeView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: HomeModel?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    LastBackupCard(
                        entry: model?.lastEntry,
                        isLoading: model?.isLoading ?? true
                    )
                    LastOperationCard(
                        operation: model?.lastOperation,
                        isLoading: model?.isLoading ?? true
                    )
                }
                .padding()
            }
            .navigationTitle("Home")
            .refreshable { await model?.refresh() }
            .alert("Error", isPresented: errorBinding) {
                Button("OK") { model?.clearError() }
            } message: {
                Text(model?.error ?? "")
            }
            .safeAreaInset(edge: .bottom) {
                startBackupBar
            }
            .task { await loadAndObserve() }
        }
    }

    private var startBackupBar: some View {
        VStack {
            Button {
                Task { await model?.startBackup() }
            } label: {
                Label("Start Backup", systemImage: "square.and.arrow.up")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 32)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!canStartBackup)
        }
        .padding()
        .background(.bar)
    }

    private var canStartBackup: Bool {
        guard let model else { return false }
        return model.firstDefinition != nil && !model.startingBackup
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { model?.error != nil },
            set: { if !$0 { model?.clearError() } }
        )
    }

    private func loadAndObserve() async {
        if model == nil, let session = container.session {
            model = HomeModel(
                session: session,
                trackers: container.trackers,
                ruleRepository: container.ruleRepository
            )
        }
        await model?.start()
    }
}

#Preview {
    HomeView()
        .environment(AppContainer())
}
