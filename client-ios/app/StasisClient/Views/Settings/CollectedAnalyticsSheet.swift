import StasisClientLib
import SwiftUI

struct CollectedAnalyticsSheet: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    @State private var model: CollectedAnalyticsModel?

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Collected Analytics")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { dismiss() }
                    }
                }
                .task { await loadIfNeeded() }
        }
        .toastLayer()
    }

    @ViewBuilder
    private var content: some View {
        if let model {
            switch model.state {
            case .loading:
                ProgressView("Loading…").frame(maxWidth: .infinity, maxHeight: .infinity)
            case .loaded(let entry):
                loadedContent(entry)
            case .failed(let message):
                ContentUnavailableView(
                    "Failed to load",
                    systemImage: "exclamationmark.triangle",
                    description: Text(message)
                )
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private func loadedContent(_ entry: AnalyticsEntry) -> some View {
        Form {
            entrySection(entry)
            runtimeSection(entry.runtime)
            eventsSection(entry.events)
            failuresSection(entry.failures)
            actionsSection(entry)
        }
    }

    private func entrySection(_ entry: AnalyticsEntry) -> some View {
        Section("Entry") {
            IdLabeledContent("Id", value: entry.runtime.id)
            LabeledContent("Created", value: entry.created.formatted(date: .abbreviated, time: .shortened))
            LabeledContent("Updated", value: entry.updated.formatted(date: .abbreviated, time: .shortened))
        }
    }

    @ViewBuilder
    private func runtimeSection(_ runtime: AnalyticsEntry.RuntimeInformation) -> some View {
        let appParts = runtime.app.split(separator: ";").map(String.init)
        let osParts = runtime.os.split(separator: ";").map(String.init)
        Section("Runtime") {
            if appParts.count >= 2 {
                LabeledContent("App", value: appParts[0])
                LabeledContent("Version", value: appParts[1])
            } else {
                LabeledContent("App", value: runtime.app)
            }
            if appParts.count >= 3, let millis = Int64(appParts[2]) {
                LabeledContent(
                    "Build",
                    value: Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
                        .formatted(date: .abbreviated, time: .shortened)
                )
            }
            if osParts.count >= 3 {
                LabeledContent("OS", value: osParts[0])
                LabeledContent("OS Version", value: osParts[1])
                LabeledContent("Arch", value: osParts[2])
            } else {
                LabeledContent("OS", value: runtime.os)
            }
        }
    }

    private func eventsSection(_ events: [AnalyticsEntry.Event]) -> some View {
        Section("Events (\(events.count))") {
            if events.isEmpty {
                Text("No events collected.").foregroundStyle(.secondary).font(.caption)
            } else {
                DisclosureGroup("Show events") {
                    ForEach(events.sorted(by: { $0.id < $1.id }), id: \.id) { event in
                        VStack(alignment: .leading, spacing: 2) {
                            Text("#\(event.id)").font(.caption2.weight(.semibold))
                            Text(event.event).font(.caption.monospaced()).textSelection(.enabled)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
    }

    private func failuresSection(_ failures: [AnalyticsEntry.Failure]) -> some View {
        Section("Failures (\(failures.count))") {
            if failures.isEmpty {
                Text("No failures collected.").foregroundStyle(.secondary).font(.caption)
            } else {
                DisclosureGroup("Show failures") {
                    ForEach(failures.sorted(by: { $0.timestamp < $1.timestamp }), id: \.timestamp) { failure in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(failure.timestamp.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption2.weight(.semibold))
                            Text(failure.message).font(.caption.monospaced()).textSelection(.enabled)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func actionsSection(_ entry: AnalyticsEntry) -> some View {
        Section {
            Button {
                copyEntry(entry)
            } label: {
                Label("Copy as JSON", systemImage: "doc.on.doc")
            }
            Button {
                Task { await sendNow() }
            } label: {
                HStack {
                    Label("Send Now", systemImage: "paperplane")
                    if model?.sendInProgress == true {
                        Spacer()
                        ProgressView()
                    }
                }
            }
            .disabled(model?.sendInProgress == true)
        } footer: {
            Text("Sending uses your configured analytics endpoint and may not complete immediately.")
        }
    }

    private func loadIfNeeded() async {
        if model == nil {
            model = CollectedAnalyticsModel(collector: container.analyticsCollector)
        }
        await model?.load()
    }

    private func sendNow() async {
        await model?.send()
        await model?.load()
        toasts.show("Analytics sent")
    }

    private func copyEntry(_ entry: AnalyticsEntry) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(entry.asJson()),
              let json = String(data: data, encoding: .utf8)
        else { return }
        UIPasteboard.general.string = json
        toasts.show("Copied to clipboard")
    }
}

#if DEBUG
#Preview {
    CollectedAnalyticsSheet()
        .environment(AppContainer())
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
}
#endif
