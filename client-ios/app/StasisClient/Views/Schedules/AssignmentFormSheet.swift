import StasisClientLib
import SwiftUI

struct AssignmentFormSheet: View {
    let scheduleId: ScheduleId
    let hasExistingBackup: Bool
    let definitions: [DatasetDefinition]
    let onSave: (ActiveSchedule) async -> Bool

    @Environment(\.dismiss) private var dismiss

    @State private var selectedDefinition: DatasetDefinitionId?
    @State private var isSaving: Bool = false
    @State private var validationError: String?

    init(
        scheduleId: ScheduleId,
        hasExistingBackup: Bool,
        definitions: [DatasetDefinition],
        onSave: @escaping (ActiveSchedule) async -> Bool
    ) {
        self.scheduleId = scheduleId
        self.hasExistingBackup = hasExistingBackup
        self.definitions = definitions
        self.onSave = onSave
        _selectedDefinition = State(initialValue: definitions.first?.id)
    }

    var body: some View {
        NavigationStack {
            Form {
                if hasExistingBackup {
                    Section {
                        ContentUnavailableView(
                            "Already Assigned",
                            systemImage: "checkmark.seal",
                            description: Text("This schedule already has a backup assignment.")
                        )
                    }
                } else {
                    Section("Definition") {
                        if definitions.isEmpty {
                            Text("No dataset definitions available.")
                                .foregroundStyle(.secondary)
                        } else {
                            Picker("Definition", selection: definitionBinding) {
                                Text("None").tag(DatasetDefinitionId?.none)
                                ForEach(definitions, id: \.id) { definition in
                                    Text(definitionLabel(definition)).tag(Optional(definition.id))
                                }
                            }
                            .pickerStyle(.menu)
                        }
                    }
                    if let validationError {
                        Section {
                            Text(validationError)
                                .foregroundStyle(.red)
                                .font(.caption)
                        }
                    }
                }
            }
            .navigationTitle("Assign Backup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(isSaving || hasExistingBackup)
                }
            }
        }
    }

    private var definitionBinding: Binding<DatasetDefinitionId?> {
        Binding(
            get: { selectedDefinition },
            set: { selectedDefinition = $0 }
        )
    }

    private func definitionLabel(_ definition: DatasetDefinition) -> String {
        "\(definition.info) · \(StatusFormatters.shortId(definition.id))"
    }

    private func save() async {
        guard let definition = selectedDefinition else {
            validationError = "Select a definition."
            return
        }
        validationError = nil
        isSaving = true
        let active = ActiveSchedule(
            id: 0,
            assignment: .backup(schedule: scheduleId, definition: definition, entities: []),
            lastFiredAt: nil
        )
        let success = await onSave(active)
        isSaving = false
        if success { dismiss() }
    }
}

#if DEBUG
#Preview("create") {
    AssignmentFormSheet(
        scheduleId: UUID(),
        hasExistingBackup: false,
        definitions: [
            DatasetDefinition(
                id: UUID(), info: "Photos", device: UUID(),
                redundantCopies: 1,
                existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
                removedVersions: .init(policy: .all, duration: SecondsDuration(3)),
                created: .now, updated: .now
            )
        ],
        onSave: { _ in true }
    )
}

#Preview("already assigned") {
    AssignmentFormSheet(
        scheduleId: UUID(),
        hasExistingBackup: true,
        definitions: [],
        onSave: { _ in true }
    )
}
#endif
