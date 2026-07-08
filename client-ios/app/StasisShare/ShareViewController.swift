import UIKit

final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let label = UILabel()
        label.text = "Saving to stasis…"
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
        Task { await ingestAndFinish() }
    }

    private func ingestAndFinish() async {
        if let inbox = DropInbox.default {
            let providers = (extensionContext?.inputItems as? [NSExtensionItem])?
                .flatMap { $0.attachments ?? [] } ?? []
            _ = await DropIngest.ingest(items: providers, into: inbox)
        }
        extensionContext?.completeRequest(returningItems: nil)
    }
}
