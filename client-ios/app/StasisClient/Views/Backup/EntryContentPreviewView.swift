import SwiftUI
import UIKit

struct EntryContentPreviewView: View {
    @State private var model: EntryContentModel

    init(model: EntryContentModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        Form {
            switch model.state {
            case .loading:
                Section { HStack { Spacer(); ProgressView(); Spacer() } }
            case .sections(let sections):
                ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                    Section(section.title) {
                        ForEach(Array(section.fields.enumerated()), id: \.offset) { _, field in
                            LabeledContent(field.label, value: field.value)
                                .textSelection(.enabled)
                        }
                    }
                }
            case .image(let data):
                Section {
                    if let image = UIImage(data: data) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("Unable to render image").foregroundStyle(.secondary)
                    }
                }
            case .text(let text):
                Section { Text(text).font(.callout.monospaced()).textSelection(.enabled) }
            case .unsupported(let bytes):
                Section {
                    Text("Content available (\(StatusFormatters.bytes(Int64(bytes)))). No inline preview.")
                        .foregroundStyle(.secondary)
                }
            case .tooLarge(let size):
                Section {
                    Text("Too large to preview (\(StatusFormatters.bytes(size))).")
                        .foregroundStyle(.secondary)
                }
            case .unavailable:
                Section { Text("No content to preview.").foregroundStyle(.secondary) }
            case .failed(let message):
                Section { Text(message).foregroundStyle(.red) }
            }
        }
        .navigationTitle(model.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.load() }
    }
}
