import SwiftUI

enum SettingsIntervalOptions {
    static let allShort: [TimeInterval] = [
        15, 30, 60, 3 * 60, 5 * 60, 15 * 60, 30 * 60, 60 * 60, 2 * 60 * 60
    ]

    static let allLong: [TimeInterval] = [
        60, 5 * 60, 15 * 60, 30 * 60, 60 * 60, 2 * 60 * 60, 6 * 60 * 60, 24 * 60 * 60
    ]

    static func label(for seconds: TimeInterval) -> String {
        let duration = Duration.seconds(Int(seconds))
        return duration.formatted(.units(allowed: [.hours, .minutes, .seconds], width: .abbreviated))
    }
}

struct PlaceholderActionRow: View {
    let title: String

    var body: some View {
        HStack {
            Text(title).foregroundStyle(.secondary)
            Spacer()
            Text("Coming soon")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
    }
}

struct IntervalPicker: View {
    let title: String
    @Binding var seconds: TimeInterval
    let options: [TimeInterval]

    var body: some View {
        Picker(title, selection: $seconds) {
            ForEach(options, id: \.self) { value in
                Text(SettingsIntervalOptions.label(for: value)).tag(value)
            }
        }
    }
}
