import SwiftUI

struct DateTimeFormatSection: View {
    @AppStorage(Settings.Keys.dateTimeFormat)
    private var dateTimeFormat: Settings.DateTimeFormat = Settings.Defaults.dateTimeFormat

    var body: some View {
        Section("Date / Time") {
            Picker("Format", selection: $dateTimeFormat) {
                Text("System").tag(Settings.DateTimeFormat.system)
                Text("ISO").tag(Settings.DateTimeFormat.iso)
            }
        }
    }
}

#Preview {
    Form { DateTimeFormatSection() }
}
