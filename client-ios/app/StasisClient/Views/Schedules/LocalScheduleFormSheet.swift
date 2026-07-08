import StasisClientLib
import SwiftUI

struct LocalScheduleFormSheet: View {
    enum Mode: Hashable {
        case create
        case edit(Schedule)
    }

    let mode: Mode
    let onSave: (Schedule) async -> Bool

    @Environment(\.dismiss) private var dismiss

    @State private var info: String
    @State private var start: Date
    @State private var intervalAmount: Int
    @State private var intervalUnit: DurationUnit
    @State private var isSaving: Bool = false
    @State private var validationError: String?

    init(mode: Mode, onSave: @escaping (Schedule) async -> Bool) {
        self.mode = mode
        self.onSave = onSave
        switch mode {
        case .create:
            _info = State(initialValue: "")
            _start = State(initialValue: Self.defaultStart())
            _intervalAmount = State(initialValue: 1)
            _intervalUnit = State(initialValue: .hours)
        case .edit(let schedule):
            _info = State(initialValue: schedule.info)
            _start = State(initialValue: Self.dateFrom(schedule.start) ?? Self.defaultStart())
            let unit = DurationUnit.best(forSeconds: schedule.interval.value)
            _intervalAmount = State(initialValue: max(1, Int(schedule.interval.value / unit.inSeconds)))
            _intervalUnit = State(initialValue: unit)
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Info") {
                    TextField("Info", text: $info)
                        .textInputAutocapitalization(.sentences)
                }
                Section("Start") {
                    DatePicker("Start", selection: $start, displayedComponents: [.date, .hourAndMinute])
                }
                Section("Interval") {
                    Stepper("Every: \(intervalAmount)", value: $intervalAmount, in: 1...999)
                    Picker("Unit", selection: $intervalUnit) {
                        ForEach(DurationUnit.allCases) { unit in
                            Text(unit.label).tag(unit)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(isSaving)
                }
            }
            .submittingOverlay(isSaving)
        }
    }

    private var title: String {
        switch mode {
        case .create: "New Schedule"
        case .edit: "Edit Schedule"
        }
    }

    private func save() async {
        let trimmed = info.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            validationError = "Info cannot be empty."
            return
        }
        guard intervalAmount > 0 else {
            validationError = "Interval must be greater than zero."
            return
        }
        validationError = nil
        isSaving = true
        let id: ScheduleId
        let created: Date
        switch mode {
        case .create:
            id = UUID()
            created = .now
        case .edit(let existing):
            id = existing.id
            created = existing.created
        }
        let schedule = Schedule(
            id: id,
            info: trimmed,
            isPublic: false,
            start: LocalDateTime(string: start),
            interval: SecondsDuration(Int64(intervalAmount) * intervalUnit.inSeconds),
            created: created,
            updated: .now
        )
        let success = await onSave(schedule)
        isSaving = false
        if success { dismiss() }
    }

    private static func defaultStart() -> Date {
        Calendar.current.date(byAdding: .hour, value: 1, to: .now) ?? .now
    }

    private static func dateFrom(_ value: LocalDateTime) -> Date? {
        Calendar.current.date(from: value.components)
    }

    enum DurationUnit: String, CaseIterable, Identifiable, Hashable {
        case minutes, hours, days

        var id: Self { self }

        var inSeconds: Int64 {
            switch self {
            case .minutes: 60
            case .hours: 3600
            case .days: 86_400
            }
        }

        var label: String {
            switch self {
            case .minutes: "Minutes"
            case .hours: "Hours"
            case .days: "Days"
            }
        }

        static func best(forSeconds total: Int64) -> DurationUnit {
            if total > 0, total.isMultiple(of: 86_400) { return .days }
            if total > 0, total.isMultiple(of: 3600) { return .hours }
            return .minutes
        }
    }
}

extension LocalDateTime {
    init(string date: Date, calendar: Calendar = .current) {
        let components = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute, .second], from: date
        )
        let value = String(
            format: "%04d-%02d-%02dT%02d:%02d:%02d",
            components.year ?? 1970,
            components.month ?? 1,
            components.day ?? 1,
            components.hour ?? 0,
            components.minute ?? 0,
            components.second ?? 0
        )
        self.init(value)
    }
}

#if DEBUG
#Preview("create") {
    LocalScheduleFormSheet(mode: .create, onSave: { _ in true })
}

#Preview("edit") {
    LocalScheduleFormSheet(
        mode: .edit(Schedule(
            id: UUID(),
            info: "Hourly photos",
            isPublic: false,
            start: LocalDateTime("2026-06-11T08:00:00"),
            interval: SecondsDuration(3600),
            created: .now,
            updated: .now
        )),
        onSave: { _ in true }
    )
}
#endif
