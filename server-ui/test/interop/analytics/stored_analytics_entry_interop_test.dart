import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/analytics/analytics_entry_event.dart';
import 'package:server_ui/model/analytics/analytics_entry_failure.dart';
import 'package:server_ui/model/analytics/analytics_entry_runtime_information.dart';
import 'package:server_ui/model/analytics/stored_analytics_entry.dart';

import '../assertions.dart';

void main() {
  group('StoredAnalyticsEntry interop', () {
    test('decode and re-encode a stored entry', () {
      assertInterop(
        domain: 'analytics',
        resource: 'StoredAnalyticsEntry',
        matches: StoredAnalyticsEntry(
          id: 'ac676261-a13e-44ce-bf0d-ac20147ce742',
          runtime: const AnalyticsEntryRuntimeInformation(
            id: '10136ad4-c1c2-469b-94b9-79af1e2b939e',
            app: 'stasis-client;1.0.0',
            jre: 'none',
            os: 'linux;6.1.0;x86_64',
          ),
          events: const [
            AnalyticsEntryEvent(id: 0, event: 'backup_started'),
          ],
          failures: [
            AnalyticsEntryFailure(
              message: 'connection refused',
              timestamp: DateTime.parse('2026-04-10T08:17:30Z'),
            ),
          ],
          created: DateTime.parse('2026-04-10T08:15:00Z'),
          updated: DateTime.parse('2026-04-10T08:18:00Z'),
          received: DateTime.parse('2026-04-10T08:18:05Z'),
        ),
        fromJson: StoredAnalyticsEntry.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
