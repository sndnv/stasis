import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/model/datasets/entity_metadata.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('A DatasetMetadata should', () {
    test('provide extractor functions', () {
      final metadata = DatasetMetadata(
        contentChanged: {
          '/some/path/01': FileEntityMetadata(
            path: '/some/path/01',
            size: 1024,
            link: '/a/b/c',
            isHidden: false,
            created: DateTime.parse('2020-10-01T01:02:03'),
            updated: DateTime.parse('2020-10-01T01:02:04'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '446',
            checksum: 42,
            crates: {
              '/some/path/01_0': 'some-id',
            },
            compression: 'gzip',
            entityType: 'file',
          ),
          '/some/path/02': FileEntityMetadata(
            path: '/some/path/02',
            size: 1024 * 32,
            link: null,
            isHidden: true,
            created: DateTime.parse('2020-10-01T01:02:05'),
            updated: DateTime.parse('2020-10-01T01:02:06'),
            owner: 'test-user',
            group: 'test-group',
            permissions: '456',
            checksum: 43,
            crates: {
              '/some/path/02_0': 'some-id-0',
              '/some/path/02_1': 'some-id-1',
            },
            compression: 'deflate',
            entityType: 'file',
          ),
        },
        metadataChanged: {},
        filesystem: const FilesystemMetadata(
          entities: {},
        ),
      );

      expect(metadata.contentChangedBytes, 1024 * (1 + 32));
    });
  });
}
