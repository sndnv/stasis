import StasisClientLib
import SwiftUI

struct DefinitionFormSheet: View {
    enum Mode: Equatable {
        case create(device: DeviceId)
        case edit(DatasetDefinition)
    }

    let mode: Mode
    let onCreate: (CreateDatasetDefinition) async -> Bool
    let onUpdate: (DatasetDefinitionId, UpdateDatasetDefinition) async -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var info: String
    @State private var redundantCopies: Int
    @State private var existing: RetentionFormState
    @State private var removed: RetentionFormState
    @State private var saving: Bool = false

    static let defaultRedundantCopies: Int = 2

    init(
        mode: Mode,
        onCreate: @escaping (CreateDatasetDefinition) async -> Bool,
        onUpdate: @escaping (DatasetDefinitionId, UpdateDatasetDefinition) async -> Bool
    ) {
        self.mode = mode
        self.onCreate = onCreate
        self.onUpdate = onUpdate
        switch mode {
        case .create:
            _info = State(initialValue: "")
            _redundantCopies = State(initialValue: Self.defaultRedundantCopies)
            _existing = State(initialValue: RetentionFormState.defaultExisting)
            _removed = State(initialValue: RetentionFormState.defaultRemoved)
        case .edit(let definition):
            _info = State(initialValue: definition.info)
            _redundantCopies = State(initialValue: definition.redundantCopies)
            _existing = State(initialValue: RetentionFormState(definition.existingVersions))
            _removed = State(initialValue: RetentionFormState(definition.removedVersions))
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Info") {
                    TextField("Name", text: $info)
                        .textInputAutocapitalization(.sentences)
                }
                Section("Storage") {
                    Stepper("Redundant Copies: \(redundantCopies)", value: $redundantCopies, in: 1...10)
                        .disabled(true)
                }
                retentionSection(title: "Existing Versions", state: $existing)
                retentionSection(title: "Removed Versions", state: $removed)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .topBarLeading) {
                    HelpButton(topic: .definitionForm)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(!canSave)
                }
            }
            .submittingOverlay(saving)
        }
    }

    private var title: String {
        switch mode {
        case .create: "New Definition"
        case .edit: "Edit Definition"
        }
    }

    private var canSave: Bool {
        !info.trimmingCharacters(in: .whitespaces).isEmpty && !saving
    }

    private func retentionSection(title: String, state: Binding<RetentionFormState>) -> some View {
        Section(title) {
            Picker("Policy", selection: state.policy) {
                ForEach(RetentionFormState.PolicyKind.allCases, id: \.self) { kind in
                    Text(kind.label).tag(kind)
                }
            }
            if state.wrappedValue.policy == .atMost {
                Stepper("Versions: \(state.wrappedValue.versions)", value: state.versions, in: 1...100)
            }
            Stepper("Duration: \(state.wrappedValue.durationAmount)", value: state.durationAmount, in: 1...999)
            Picker("Unit", selection: state.durationUnit) {
                ForEach(RetentionFormState.DurationUnit.allCases) { unit in
                    Text(unit.label).tag(unit)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let trimmedInfo = info.trimmingCharacters(in: .whitespaces)
        let existingRetention = existing.toRetention()
        let removedRetention = removed.toRetention()
        switch mode {
        case .create(let device):
            let request = CreateDatasetDefinition(
                info: trimmedInfo, device: device,
                redundantCopies: redundantCopies,
                existingVersions: existingRetention,
                removedVersions: removedRetention
            )
            if await onCreate(request) { dismiss() }
        case .edit(let definition):
            let request = UpdateDatasetDefinition(
                info: trimmedInfo,
                redundantCopies: redundantCopies,
                existingVersions: existingRetention,
                removedVersions: removedRetention
            )
            if await onUpdate(definition.id, request) { dismiss() }
        }
    }
}

struct RetentionFormState: Equatable {
    enum PolicyKind: Equatable, Hashable, CaseIterable {
        case all, latestOnly, atMost

        var label: String {
            switch self {
            case .all: "All"
            case .latestOnly: "Latest Only"
            case .atMost: "At Most"
            }
        }
    }

    enum DurationUnit: String, CaseIterable, Identifiable, Hashable {
        case seconds, minutes, hours, days

        var id: Self { self }

        var inSeconds: Int64 {
            switch self {
            case .seconds: 1
            case .minutes: 60
            case .hours: 3600
            case .days: 86_400
            }
        }

        var label: String {
            switch self {
            case .seconds: "Seconds"
            case .minutes: "Minutes"
            case .hours: "Hours"
            case .days: "Days"
            }
        }
    }

    var policy: PolicyKind
    var versions: Int
    var durationAmount: Int
    var durationUnit: DurationUnit

    static let defaultExisting = RetentionFormState(
        policy: .atMost, versions: 5, durationAmount: 30, durationUnit: .days
    )
    static let defaultRemoved = RetentionFormState(
        policy: .latestOnly, versions: 1, durationAmount: 14, durationUnit: .days
    )

    init(policy: PolicyKind, versions: Int, durationAmount: Int, durationUnit: DurationUnit) {
        self.policy = policy
        self.versions = versions
        self.durationAmount = durationAmount
        self.durationUnit = durationUnit
    }

    init(_ retention: DatasetDefinition.Retention) {
        switch retention.policy {
        case .all: self.policy = .all; self.versions = 1
        case .latestOnly: self.policy = .latestOnly; self.versions = 1
        case .atMost(let versions): self.policy = .atMost; self.versions = versions
        }
        let total = retention.duration.value
        if total > 0, total.isMultiple(of: 86_400) {
            self.durationUnit = .days
            self.durationAmount = Int(total / 86_400)
        } else if total > 0, total.isMultiple(of: 3600) {
            self.durationUnit = .hours
            self.durationAmount = Int(total / 3600)
        } else if total > 0, total.isMultiple(of: 60) {
            self.durationUnit = .minutes
            self.durationAmount = Int(total / 60)
        } else {
            self.durationUnit = .seconds
            self.durationAmount = max(1, Int(total))
        }
    }

    func toRetention() -> DatasetDefinition.Retention {
        let policyValue: DatasetDefinition.Retention.Policy = switch policy {
        case .all: .all
        case .latestOnly: .latestOnly
        case .atMost: .atMost(versions: versions)
        }
        return DatasetDefinition.Retention(
            policy: policyValue,
            duration: SecondsDuration(Int64(durationAmount) * durationUnit.inSeconds)
        )
    }
}

#if DEBUG
#Preview("create") {
    DefinitionFormSheet(
        mode: .create(device: MockConfig.device),
        onCreate: { _ in true },
        onUpdate: { _, _ in true }
    )
}

#Preview("edit") {
    DefinitionFormSheet(
        mode: .edit(MockServerApiEndpointClient.defaultDefinition),
        onCreate: { _ in true },
        onUpdate: { _, _ in true }
    )
}
#endif
