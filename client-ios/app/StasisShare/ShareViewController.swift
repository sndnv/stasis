import StasisClientLib
import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private static let dismissDelay: Duration = .milliseconds(500)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let label = UILabel()
        label.text = "Saved to stasis"
        label.font = .preferredFont(forTextStyle: .title2)
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)

        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        Task { [weak self] in
            try? await Task.sleep(for: Self.dismissDelay)
            await MainActor.run {
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        }
    }
}
