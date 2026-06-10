import SwiftUI

struct AboutView: View {
    @Environment(AppContainer.self) private var container

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    header
                    Divider().padding(.horizontal, 48)
                    license
                    Divider().padding(.horizontal, 48)
                    footer
                }
                .multilineTextAlignment(.center)
                .padding()
            }
            .navigationTitle("About")
        }
    }

    private var header: some View {
        VStack(spacing: 12) {
            Image("StasisLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 160, height: 160)
            Text("stasis")
                .font(.largeTitle.weight(.semibold))
            Text("Version \(container.appInfo.version)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var license: some View {
        VStack(spacing: 16) {
            Text("This project is licensed under the Apache License, Version 2.0")
                .font(.subheadline.weight(.medium))
            VStack(spacing: 4) {
                Text("Copyright 2018")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Link("github.com/sndnv", destination: URL(string: "https://github.com/sndnv")!)
                    .font(.caption)
            }
            VStack(spacing: 8) {
                Text(Self.licensePreamble)
                Link(
                    "apache.org/licenses/LICENSE-2.0",
                    destination: URL(string: "http://www.apache.org/licenses/LICENSE-2.0")!
                )
                Text(Self.licenseDisclaimer)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
    }

    private static let licensePreamble: String = """
        Licensed under the Apache License, Version 2.0 (the "License"); \
        you may not use this file except in compliance with the License. \
        You may obtain a copy of the License at
        """

    private static let licenseDisclaimer: String = """
        Unless required by applicable law or agreed to in writing, \
        software distributed under the License is distributed on an "AS IS" BASIS, \
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. \
        See the License for the specific language governing permissions and \
        limitations under the License.
        """

    private var footer: some View {
        VStack(spacing: 4) {
            Text("For more information, visit")
                .font(.subheadline)
            Link(
                "github.com/sndnv/stasis",
                destination: URL(string: "https://github.com/sndnv/stasis")!
            )
            .font(.subheadline.weight(.medium))
        }
    }
}

#Preview {
    AboutView()
        .environment(AppContainer())
}
