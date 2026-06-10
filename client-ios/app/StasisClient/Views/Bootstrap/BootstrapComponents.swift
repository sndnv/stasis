import SwiftUI

struct BootstrapLogo: View {
    var body: some View {
        HStack {
            Spacer()
            Image("StasisLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 160, height: 160)
            Spacer()
        }
    }
}

struct BootstrapInfoButton: View {
    let icon: String
    let title: String
    let message: String

    @State private var showing: Bool = false

    init(icon: String = "info.circle", title: String, message: String) {
        self.icon = icon
        self.title = title
        self.message = message
    }

    var body: some View {
        Button {
            showing = true
        } label: {
            Image(systemName: icon)
                .foregroundStyle(.secondary)
        }
        .buttonStyle(.plain)
        .alert(title, isPresented: $showing) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(message)
        }
    }
}

struct BootstrapStepRow: View {
    @Binding var path: [BootstrapStep]
    let step: String
    let nextLabel: String
    let nextDisabled: Bool
    let onNext: () -> Void

    init(
        path: Binding<[BootstrapStep]>,
        step: String,
        nextLabel: String = "Next",
        nextDisabled: Bool = false,
        onNext: @escaping () -> Void
    ) {
        self._path = path
        self.step = step
        self.nextLabel = nextLabel
        self.nextDisabled = nextDisabled
        self.onNext = onNext
    }

    var body: some View {
        HStack {
            Button("Back") {
                if !path.isEmpty { path.removeLast() }
            }
            Spacer()
            Text(step).foregroundStyle(.secondary)
            Spacer()
            Button(nextLabel, action: onNext)
                .disabled(nextDisabled)
                .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

struct BootstrapSwitchLabel: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.body.weight(.semibold))
            Text(subtitle).font(.callout.italic()).foregroundStyle(.secondary)
        }
    }
}
