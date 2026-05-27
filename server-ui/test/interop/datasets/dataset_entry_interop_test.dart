import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/datasets/dataset_entry.dart';

import '../assertions.dart';

void main() {
  group('DatasetEntry interop', () {
    test('decode and re-encode an entry', () {
      assertInterop(
        domain: 'datasets',
        resource: 'DatasetEntry',
        matches: DatasetEntry(
          id: '77eebe09-aea1-4e2e-9cd0-3b5919d43def',
          definition: '6c041cb6-94af-4649-9091-8e39d330527e',
          device: 'caac9b3b-dbf9-41c1-9fee-3aa0e5bbb70b',
          data: const {'d0c7ddc7-f639-4686-a3b5-465c61cdf94b'},
          metadata: '6bb8d69c-5ae4-4876-9836-35e66a1c9ecb',
          changes: 42,
          size: 1048576,
          created: DateTime.parse('2026-01-22T09:00:00Z'),
        ),
        fromJson: DatasetEntry.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
