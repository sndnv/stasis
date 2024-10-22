import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/utils/chrono_unit.dart';
import 'package:server_ui/utils/file_size_unit.dart';
import 'package:server_ui/utils/pair.dart';

void main() {
  group('Rendering components should', () {
    test('convert durations to fields', () {
      expect(const Duration(seconds: 0).toFields(), Pair(0, ChronoUnit.seconds));
      expect(const Duration(seconds: 1).toFields(), Pair(1, ChronoUnit.seconds));
      expect(const Duration(seconds: 59).toFields(), Pair(59, ChronoUnit.seconds));
      expect(const Duration(seconds: 61).toFields(), Pair(61, ChronoUnit.seconds));

      expect(const Duration(seconds: 60).toFields(), Pair(1, ChronoUnit.minutes));
      expect(const Duration(minutes: 1).toFields(), Pair(1, ChronoUnit.minutes));
      expect(const Duration(minutes: 59).toFields(), Pair(59, ChronoUnit.minutes));
      expect(const Duration(minutes: 61).toFields(), Pair(61, ChronoUnit.minutes));

      expect(const Duration(minutes: 60).toFields(), Pair(1, ChronoUnit.hours));
      expect(const Duration(hours: 1).toFields(), Pair(1, ChronoUnit.hours));
      expect(const Duration(hours: 23).toFields(), Pair(23, ChronoUnit.hours));
      expect(const Duration(hours: 25).toFields(), Pair(25, ChronoUnit.hours));

      expect(const Duration(hours: 24).toFields(), Pair(1, ChronoUnit.days));
      expect(const Duration(days: 1).toFields(), Pair(1, ChronoUnit.days));
      expect(const Duration(days: 2).toFields(), Pair(2, ChronoUnit.days));
      expect(const Duration(days: 3).toFields(), Pair(3, ChronoUnit.days));
      expect(const Duration(days: 99).toFields(), Pair(99, ChronoUnit.days));
    });

    test('convert durations to strings', () {
      expect(const Duration(seconds: 0).render(), '0 seconds');
      expect(const Duration(seconds: 1).render(), '1 second');
      expect(const Duration(seconds: 2).render(), '2 seconds');
    });

    test('convert DateTimes to strings', () {
      final timestamp = DateTime(2020, 12, 31, 23, 45, 30);

      expect(timestamp.render(), '2020-12-31 23:45');
      expect(timestamp.renderAsDate(), '2020-12-31');
      expect(timestamp.renderAsTime(), '23:45');
    });

    test('convert DatasetDefinitions Retentions to strings', () {
      expect(
        const Retention(
          policy: Policy(policyType: 'all', versions: null),
          duration: Duration(seconds: 1),
        ).render(),
        '1 second,\nall versions',
      );

      expect(
        const Retention(
          policy: Policy(policyType: 'at-most', versions: 1),
          duration: Duration(seconds: 2),
        ).render(),
        '2 seconds,\nat most 1 version',
      );

      expect(
        const Retention(
          policy: Policy(policyType: 'at-most', versions: 2),
          duration: Duration(seconds: 3),
        ).render(),
        '3 seconds,\nat most 2 versions',
      );

      expect(
        const Retention(
          policy: Policy(policyType: 'latest-only', versions: null),
          duration: Duration(seconds: 4),
        ).render(),
        '4 seconds,\nlatest version only',
      );
    });

    test('convert numbers to file size fields', () {
      expect(0.toFields(), Pair(0, FileSizeUnit.bytes));
      expect(1.toFields(), Pair(1, FileSizeUnit.bytes));
      expect(1000.toFields(), Pair(1, FileSizeUnit.kilobytes));
      expect((10 * 1000).toFields(), Pair(10, FileSizeUnit.kilobytes));
      expect((42 * 1000 * 1000).toFields(), Pair(42, FileSizeUnit.megabytes));
      expect((21 * 1000 * 1000 * 1000).toFields(), Pair(21, FileSizeUnit.gigabytes));
      expect((1 * 1000 * 1000 * 1000 * 1000).toFields(), Pair(1, FileSizeUnit.terabytes));
      expect((1 * 1000 * 1000 * 1000 * 1000 * 1000).toFields(), Pair(1, FileSizeUnit.petabytes));
    });

    test('convert numbers to file size strings', () {
      expect(0.renderFileSize(), '0 B');
      expect(1.renderFileSize(), '1 B');
      expect(1000.renderFileSize(), '1 kB');
      expect(1024.renderFileSize(), '1 kB');
      expect((10 * 1024).renderFileSize(), '10.2 kB');
    });

    test('convert numbers to strings', () {
      expect(0.renderNumber(), '0');
      expect(1.renderNumber(), '1');
      expect(100.renderNumber(), '100');
      expect(1000.renderNumber(), '1K');
      expect(10000.renderNumber(), '10K');
    });
  });
}
