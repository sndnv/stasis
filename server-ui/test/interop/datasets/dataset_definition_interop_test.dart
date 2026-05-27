import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_dataset_definition.dart';
import 'package:server_ui/model/api/requests/update_dataset_definition.dart';
import 'package:server_ui/model/api/responses/created_dataset_definition.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';

import '../assertions.dart';

void main() {
  group('DatasetDefinition interop', () {
    test('decode and re-encode a definition', () {
      assertInterop(
        domain: 'datasets',
        resource: 'DatasetDefinition',
        matches: DatasetDefinition(
          id: '6c041cb6-94af-4649-9091-8e39d330527e',
          info: 'primary backup definition',
          device: 'caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b',
          redundantCopies: 3,
          existingVersions: const Retention(
            policy: Policy(policyType: 'at-most', versions: 5),
            duration: Duration(seconds: 604800),
          ),
          removedVersions: const Retention(
            policy: Policy(policyType: 'latest-only', versions: null),
            duration: Duration(seconds: 2592000),
          ),
          created: DateTime.parse('2026-01-15T10:30:00Z'),
          updated: DateTime.parse('2026-01-20T14:45:00Z'),
        ),
        fromJson: DatasetDefinition.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreateDatasetDefinition', () {
      assertInterop(
        domain: 'datasets',
        resource: 'CreateDatasetDefinition',
        matches: CreateDatasetDefinition(
          info: 'new backup definition',
          device: 'caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b',
          redundantCopies: 2,
          existingVersions: const Retention(
            policy: Policy(policyType: 'all', versions: null),
            duration: Duration(seconds: 7776000),
          ),
          removedVersions: const Retention(
            policy: Policy(policyType: 'at-most', versions: 7),
            duration: Duration(seconds: 1209600),
          ),
        ),
        fromJson: CreateDatasetDefinition.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateDatasetDefinition', () {
      assertInterop(
        domain: 'datasets',
        resource: 'UpdateDatasetDefinition',
        matches: UpdateDatasetDefinition(
          info: 'updated backup definition',
          redundantCopies: 4,
          existingVersions: const Retention(
            policy: Policy(policyType: 'latest-only', versions: null),
            duration: Duration(seconds: 86400),
          ),
          removedVersions: const Retention(
            policy: Policy(policyType: 'at-most', versions: 3),
            duration: Duration(seconds: 432000),
          ),
        ),
        fromJson: UpdateDatasetDefinition.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreatedDatasetDefinition', () {
      assertInterop(
        domain: 'datasets',
        resource: 'CreatedDatasetDefinition',
        matches: const CreatedDatasetDefinition(
          definition: '6d63a635-d012-4b28-9193-7bd870cc4093',
        ),
        fromJson: CreatedDatasetDefinition.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
