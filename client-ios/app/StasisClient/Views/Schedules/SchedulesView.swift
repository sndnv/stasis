import StasisClientLib
import SwiftUI

struct SchedulesView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: SchedulesModel?
    @State private var formMode: LocalScheduleFormSheet.Mode?
    @State private var deletionTarget: Schedule?
    @State private var deletionBlocked: Schedule?
    @State private var assignmentTarget: SchedulesModel.Row?

    var body: some View {
        NavigationStack {
            SchedulesViewContent(
                state: state,
                onRefresh: { await model?.refresh() },
                onClearError: { model?.clearError() },
                definitionInfo: { model?.definitionInfo($0) },
                onEdit: { schedule in formMode = .edit(schedule) },
                onDeleteRequest: { row in
                    guard let schedule = row.schedule else { return }
                    if row.assignments.isEmpty {
                        deletionTarget = schedule
                    } else {
                        deletionBlocked = schedule
                    }
                },
                onAddAssignment: { row in assignmentTarget = row },
                onRemoveAssignment: { active in
                    Task { await model?.removeAssignment(active.id) }
                }
            )
            .navigationTitle("Schedules")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { formMode = .create } label: {
                        Label("Add Schedule", systemImage: "plus")
                    }
                }
            }
            .task { await startIfNeeded() }
            .sheet(item: $formMode) { mode in
                LocalScheduleFormSheet(
                    mode: mode,
                    onSave: { schedule in await model?.saveLocalSchedule(schedule) ?? false }
                )
            }
            .sheet(item: $assignmentTarget) { row in
                let live = model?.rows.first(where: { $0.id == row.id }) ?? row
                AssignmentFormSheet(
                    scheduleId: live.id,
                    hasExistingBackup: live.hasBackupAssignment,
                    definitions: model?.definitions ?? [],
                    onSave: { active in await model?.addAssignment(active) ?? false }
                )
            }
            .confirmationDialog(
                "Delete schedule?",
                isPresented: deletionBinding,
                presenting: deletionTarget
            ) { schedule in
                Button("Delete", role: .destructive) {
                    Task { await model?.deleteLocalSchedule(schedule.id) }
                }
                Button("Cancel", role: .cancel) {}
            } message: { schedule in
                Text("Removes \(schedule.info).")
            }
            .alert(
                "Cannot Delete",
                isPresented: deletionBlockedBinding,
                presenting: deletionBlocked
            ) { _ in
                Button("OK", role: .cancel) {}
            } message: { schedule in
                Text("Remove all assignments before deleting \(schedule.info).")
            }
        }
    }

    private var state: SchedulesViewState {
        guard let model else { return .initial }
        return SchedulesViewState(
            rows: model.rows,
            isLoading: model.isLoading,
            error: model.error
        )
    }

    private var deletionBinding: Binding<Bool> {
        Binding(
            get: { deletionTarget != nil },
            set: { if !$0 { deletionTarget = nil } }
        )
    }

    private var deletionBlockedBinding: Binding<Bool> {
        Binding(
            get: { deletionBlocked != nil },
            set: { if !$0 { deletionBlocked = nil } }
        )
    }

    private func startIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = SchedulesModel(
                session: session,
                scheduler: container.backgroundScheduler,
                localScheduleRepository: container.localScheduleRepository
            )
        }
        await model?.start()
    }
}

extension LocalScheduleFormSheet.Mode: Identifiable {
    public var id: String {
        switch self {
        case .create: "create"
        case .edit(let schedule): "edit-\(schedule.id.uuidString)"
        }
    }
}

struct SchedulesViewState: Equatable {
    var rows: [SchedulesModel.Row] = []
    var isLoading: Bool = false
    var error: String?

    static let initial = SchedulesViewState(isLoading: true)
}

private struct SchedulesViewContent: View {
    let state: SchedulesViewState
    let onRefresh: () async -> Void
    let onClearError: () -> Void
    let definitionInfo: (DatasetDefinitionId) -> String?
    let onEdit: (Schedule) -> Void
    let onDeleteRequest: (SchedulesModel.Row) -> Void
    let onAddAssignment: (SchedulesModel.Row) -> Void
    let onRemoveAssignment: (ActiveSchedule) -> Void

    @State private var expanded: Set<ScheduleId> = []

    var body: some View {
        List {
            if state.isLoading && state.rows.isEmpty {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else if state.rows.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No Schedules",
                        systemImage: "clock",
                        description: Text("No public or local schedules are available.")
                    )
                }
            } else {
                ForEach(state.rows) { row in
                    ScheduleRow(
                        row: row,
                        isExpanded: expanded.contains(row.id),
                        onToggle: { toggle(row.id) },
                        definitionInfo: definitionInfo,
                        onAddAssignment: { onAddAssignment(row) },
                        onRemoveAssignment: onRemoveAssignment
                    )
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        if let schedule = row.schedule, !schedule.isPublic {
                            Button(role: .destructive) {
                                onDeleteRequest(row)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            Button {
                                onEdit(schedule)
                            } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            .tint(.indigo)
                        }
                    }
                }
            }
        }
        .refreshable { await onRefresh() }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
    }

    private func toggle(_ id: ScheduleId) {
        if expanded.contains(id) {
            expanded.remove(id)
        } else {
            expanded.insert(id)
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }
}

struct ScheduleRow: View {
    let row: SchedulesModel.Row
    let isExpanded: Bool
    let onToggle: () -> Void
    let definitionInfo: (DatasetDefinitionId) -> String?
    let onAddAssignment: () -> Void
    let onRemoveAssignment: (ActiveSchedule) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            summary
            if isExpanded {
                Divider()
                assignmentsSection
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { onToggle() }
    }

    @ViewBuilder
    private var summary: some View {
        HStack(alignment: .firstTextBaseline) {
            if row.isNext {
                Image(systemName: "arrowtriangle.right.fill")
                    .font(.caption)
                    .foregroundStyle(Color.accentColor)
                    .accessibilityLabel("Next to fire")
            }
            Text(row.schedule?.info ?? "Unknown")
                .font(.headline)
            badge
            Spacer()
            if !row.assignments.isEmpty {
                Text("\(row.assignments.count)")
                    .font(.caption.monospaced())
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Color.accentColor.opacity(0.15))
                    .clipShape(Capsule())
            }
            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                .foregroundStyle(.secondary)
        }
        if let schedule = row.schedule {
            HStack(spacing: 12) {
                if let next = schedule.nextInvocation() {
                    Label(next.formatted(date: .abbreviated, time: .shortened), systemImage: "calendar")
                }
                Label(intervalLabel(schedule.interval), systemImage: "arrow.clockwise")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)
            if !assignmentTypeLabels.isEmpty {
                Text("Active: \(assignmentTypeLabels.joined(separator: ", "))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } else {
            Text(StatusFormatters.shortId(row.id))
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var badge: some View {
        if let schedule = row.schedule {
            Text(schedule.isPublic ? "PUBLIC" : "LOCAL")
                .font(.caption2.weight(.semibold))
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background((schedule.isPublic ? Color.blue : Color.green).opacity(0.15))
                .foregroundStyle(schedule.isPublic ? Color.blue : Color.green)
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }

    @ViewBuilder
    private var assignmentsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            if row.assignments.isEmpty {
                Text("No assignments")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(row.assignments, id: \.id) { active in
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(assignmentLabel(active.assignment))
                                .font(.caption.weight(.semibold))
                            if let lastFired = active.lastFiredAt {
                                Text("Last fired \(lastFired.formatted(.relative(presentation: .named)))")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Button(role: .destructive) {
                            onRemoveAssignment(active)
                        } label: {
                            Image(systemName: "minus.circle.fill")
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }
            if canAddAssignment {
                Button {
                    onAddAssignment()
                } label: {
                    Label("Add Assignment", systemImage: "plus.circle")
                        .font(.caption)
                }
                .buttonStyle(.borderless)
            }
        }
    }

    private var canAddAssignment: Bool {
        guard row.schedule != nil else { return false }
        return !row.hasBackupAssignment
    }

    private var assignmentTypeLabels: [String] {
        Array(Set(row.assignments.map { Self.typeLabel($0.assignment) })).sorted()
    }

    private func assignmentLabel(_ assignment: OperationScheduleAssignment) -> String {
        switch assignment {
        case .backup(_, let definition, _):
            let info = definitionInfo(definition) ?? StatusFormatters.shortId(definition)
            return "Backup · \(info)"
        case .expiration: return "Expiration"
        case .validation: return "Validation"
        case .keyRotation: return "Key Rotation"
        }
    }

    private static func typeLabel(_ assignment: OperationScheduleAssignment) -> String {
        switch assignment {
        case .backup: "Backup"
        case .expiration: "Expiration"
        case .validation: "Validation"
        case .keyRotation: "Key Rotation"
        }
    }

    private func intervalLabel(_ duration: SecondsDuration) -> String {
        StatusFormatters.duration(duration)
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: SchedulesViewState

    var body: some View {
        NavigationStack {
            SchedulesViewContent(
                state: state,
                onRefresh: {},
                onClearError: {},
                definitionInfo: { _ in "Photos" },
                onEdit: { _ in },
                onDeleteRequest: { _ in },
                onAddAssignment: { _ in },
                onRemoveAssignment: { _ in }
            )
            .navigationTitle("Schedules")
        }
    }
}

private extension SchedulesModel.Row {
    static func mockPublic() -> SchedulesModel.Row {
        let schedule = Schedule(
            id: UUID(),
            info: "Daily backup",
            isPublic: true,
            start: LocalDateTime("2026-06-11T08:00:00"),
            interval: SecondsDuration(86400),
            created: .now, updated: .now
        )
        return SchedulesModel.Row(id: schedule.id, schedule: schedule, assignments: [])
    }

    static func mockLocalWithAssignment() -> SchedulesModel.Row {
        let schedule = Schedule(
            id: UUID(),
            info: "Hourly photos",
            isPublic: false,
            start: LocalDateTime("2026-06-11T08:00:00"),
            interval: SecondsDuration(3600),
            created: .now, updated: .now
        )
        let active = ActiveSchedule(
            id: 1,
            assignment: .backup(schedule: schedule.id, definition: UUID(), entities: []),
            lastFiredAt: .now.addingTimeInterval(-1800)
        )
        return SchedulesModel.Row(
            id: schedule.id,
            schedule: schedule,
            assignments: [active],
            isNext: true
        )
    }
}

#Preview("loading") {
    PreviewHarness(state: .initial)
}

#Preview("empty") {
    PreviewHarness(state: SchedulesViewState(rows: [], isLoading: false))
}

#Preview("populated") {
    PreviewHarness(state: SchedulesViewState(
        rows: [.mockPublic(), .mockLocalWithAssignment()],
        isLoading: false
    ))
}
#endif
