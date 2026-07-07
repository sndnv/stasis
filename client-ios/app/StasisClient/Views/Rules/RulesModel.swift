import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class RulesModel {
    struct Row: Identifiable, Sendable {
        let source: LibrarySource
        let isEnabled: Bool
        let permission: LibraryPermissionStatus
        let enabledCount: Int
        let totalSets: Int
        let inDefault: Bool

        var id: String { source.scheme }
    }

    private let ruleRepository: RuleRepository
    private let sources: [LibrarySource]

    private(set) var rows: [Row] = []
    private(set) var isLoading: Bool = true
    var error: String?
    var permissionDenied: LibrarySource?

    private var rules: [Rule] = []

    init(ruleRepository: RuleRepository, sources: [LibrarySource]) {
        self.ruleRepository = ruleRepository
        self.sources = sources
    }

    func clearError() { error = nil }

    func start() async { await refresh() }

    func refresh() async {
        do {
            rules = try await ruleRepository.rules()
        } catch {
            self.error = error.localizedDescription
            rules = []
        }
        recompute()
        isLoading = false
    }

    func setEnabled(_ scheme: String, _ enabled: Bool) async {
        if enabled {
            await enable(scheme)
        } else {
            await disable(scheme)
        }
    }

    func resetToDefaults() async {
        do {
            try await ruleRepository.clear()
            try await ruleRepository.bootstrap()
        } catch {
            self.error = error.localizedDescription
        }
        await refresh()
    }

    private func enable(_ scheme: String) async {
        guard let source = sources.first(where: { $0.scheme == scheme }) else { return }
        switch source.permission.status() {
        case .granted:
            await applyEnable(scheme)
        case .undetermined:
            if await source.permission.request() {
                await applyEnable(scheme)
            } else {
                permissionDenied = source
                await refresh()
            }
        case .denied:
            permissionDenied = source
            await refresh()
        }
    }

    private func applyEnable(_ scheme: String) async {
        let targets = SourceRules.setsMissing(rules, scheme: scheme)
        do {
            for definition in targets {
                _ = try await ruleRepository.put(
                    Rule(id: 0, operation: .include, source: "\(scheme):/", pattern: "*", definition: definition)
                )
            }
        } catch {
            self.error = error.localizedDescription
        }
        await refresh()
    }

    private func disable(_ scheme: String) async {
        do {
            for rule in SourceRules.matching(rules, scheme: scheme) {
                try await ruleRepository.delete(id: rule.id)
            }
        } catch {
            self.error = error.localizedDescription
        }
        await refresh()
    }

    private func recompute() {
        rows = sources.map { source in
            let scheme = source.scheme
            return Row(
                source: source,
                isEnabled: SourceRules.isEnabled(rules, scheme: scheme),
                permission: source.permission.status(),
                enabledCount: SourceRules.enabledSets(rules, scheme: scheme).count,
                totalSets: SourceRules.allSets(rules).count,
                inDefault: SourceRules.isInDefault(rules, scheme: scheme)
            )
        }
    }
}
