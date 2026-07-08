import StasisClientLib
import SwiftUI

struct HomeView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ToastCenter.self) private var toasts
    @State private var model: HomeModel?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    lastBackupSection
                    lastOperationSection
                }
                .padding()
            }
            .navigationTitle("Home")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    HelpButton(topic: .home)
                }
            }
            .navigationDestination(for: DatasetEntry.self) { entry in
                EntryDetailView(entry: entry)
            }
            .navigationDestination(for: OperationDetailKey.self) { key in
                OperationDetailView(key: key)
            }
            .refreshable { await model?.refresh() }
            .alert("Error", isPresented: errorBinding) {
                Button("OK") { model?.clearError() }
            } message: {
                Text(model?.error ?? "")
            }
            .onChange(of: model?.didStartBackup) { _, started in
                if started == true {
                    toasts.show("Backup started")
                    model?.didStartBackup = false
                }
            }
            .safeAreaInset(edge: .bottom) {
                startBackupBar
            }
            .task { await loadAndObserve() }
        }
    }

    @ViewBuilder
    private var lastBackupSection: some View {
        if let entry = model?.lastEntry {
            NavigationLink(value: entry) {
                LastBackupCard(entry: entry, isLoading: false)
            }
            .buttonStyle(.plain)
        } else {
            LastBackupCard(entry: nil, isLoading: model?.isLoading ?? true)
        }
    }

    @ViewBuilder
    private var lastOperationSection: some View {
        if let operation = model?.lastOperation {
            NavigationLink(value: OperationDetailKey(id: operation.id, type: operation.type)) {
                LastOperationCard(operation: operation, isLoading: false)
            }
            .buttonStyle(.plain)
        } else {
            LastOperationCard(operation: nil, isLoading: model?.isLoading ?? true)
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
