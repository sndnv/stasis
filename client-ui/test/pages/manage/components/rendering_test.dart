import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/chrono_unit.dart';
import 'package:stasis_client_ui/utils/file_size_unit.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/model/operations/operation.dart' as operation;

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

    test('convert durations to approximate strings', () {
      expect(const Duration(seconds: 0).renderApproximate(), '0 seconds');
      expect(const Duration(seconds: 1).renderApproximate(), '1 second');
      expect(const Duration(seconds: 2).renderApproximate(), '2 seconds');
      expect(const Duration(seconds: 119).renderApproximate(), '119 seconds');
      expect(const Duration(seconds: 120).renderApproximate(), '2 minutes');
      expect(const Duration(seconds: 121).renderApproximate(), '2 minutes');
      expect(const Duration(minutes: 119).renderApproximate(), '119 minutes');
      expect(const Duration(minutes: 120).renderApproximate(), '2 hours');
      expect(const Duration(minutes: 121).renderApproximate(), '2 hours');
      expect(const Duration(hours: 47).renderApproximate(), '47 hours');
      expect(const Duration(hours: 48).renderApproximate(), '2 days');
      expect(const Duration(hours: 49).renderApproximate(), '2 days');
      expect(const Duration(days: 364).renderApproximate(), '11 months');
      expect(const Duration(days: 365).renderApproximate(), '1 year');
      expect(const Duration(days: 366).renderApproximate(), '1 year');
      expect(const Duration(days: 1095).renderApproximate(), '3 years');
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
        '1 second, all versions',
      );

      expect(
        const Retention(
          policy: Policy(policyType: 'at-most', versions: 1),
          duration: Duration(seconds: 2),
        ).render(),
        '2 seconds, at most 1 version',
      );

      expect(
        const Retention(
          policy: Policy(policyType: 'at-most', versions: 2),
          duration: Duration(seconds: 3),
        ).render(),
        '3 seconds, at most 2 versions',
      );

      expect(
        const Retention(
          policy: Policy(policyType: 'latest-only', versions: null),
          duration: Duration(seconds: 4),
        ).render(),
        '4 seconds, latest version only',
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

    test('convert operation types to strings', () {
      expect(operation.Type.backup.render(), 'Backup');
      expect(operation.Type.recovery.render(), 'Recovery');
      expect(operation.Type.expiration.render(), 'Expiration');
      expect(operation.Type.validation.render(), 'Validation');
      expect(operation.Type.keyRotation.render(), 'Key Rotation');
      expect(operation.Type.garbageCollection.render(), 'Garbage Collection');
    });

    test('convert operation stage names to strings', () {
      expect('discovered'.toOperationStageString(), 'Discovered');
      expect('examined'.toOperationStageString(), 'Examined');
      expect('skipped'.toOperationStageString(), 'Skipped');
      expect('collected'.toOperationStageString(), 'Collected');
      expect('pending'.toOperationStageString(), 'Pending');
      expect('processed'.toOperationStageString(), 'Processed');
      expect('metadata-applied'.toOperationStageString(), 'Metadata Applied');
      expect('other'.toOperationStageString(), 'other');
    });

    test('split path strings into parent and name', () {
      expect('/'.toSplitPath(), Pair('/', '.'));
      expect(' / '.toSplitPath(), Pair('/', '.'));
      expect('////'.toSplitPath(), Pair('/', '.'));
      expect('/a'.toSplitPath(), Pair('/', 'a'));
      expect('/a/b/c'.toSplitPath(), Pair('/a/b', 'c'));
      expect('/a/b/c/'.toSplitPath(), Pair('/a/b', 'c'));
      expect('/a/b//c'.toSplitPath(), Pair('/a/b', 'c'));
    });

    test('capitalize strings', () {
      expect('abc'.capitalize(), 'Abc');
      expect('test'.capitalize(), 'Test');
      expect('123test'.capitalize(), '123test');
    });

    test('convert assignment types to strings', () {
      const backup = BackupAssignment(assignmentType: 'backup', schedule: 's-1', definition: 'test', entities: []);
      const expiration = ExpirationAssignment(assignmentType: 'expiration', schedule: 's-2');
      const validation = ValidationAssignment(assignmentType: 'validation', schedule: 's-3');
      const keyRotation = KeyRotationAssignment(assignmentType: 'key-rotation', schedule: 's-4');

      expect(backup.toAssignmentTypeString(), 'Backup');
      expect(expiration.toAssignmentTypeString(), 'Expiration');
      expect(validation.toAssignmentTypeString(), 'Validation');
      expect(keyRotation.toAssignmentTypeString(), 'Key Rotation');
    });

    test('extract assignment schedules', () {
      const Assignment backup = BackupAssignment(assignmentType: 'backup', schedule: 's-1', definition: 'test', entities: []);
      const Assignment expiration = ExpirationAssignment(assignmentType: 'expiration', schedule: 's-2');
      const Assignment validation = ValidationAssignment(assignmentType: 'validation', schedule: 's-3');
      const Assignment keyRotation = KeyRotationAssignment(assignmentType: 'key-rotation', schedule: 's-4');

      expect(backup.schedule(), 's-1');
      expect(expiration.schedule(), 's-2');
      expect(validation.schedule(), 's-3');
      expect(keyRotation.schedule(), 's-4');
    });
  });
}
