import 'dart:convert';

import 'package:stasis_client_ui/model/datasets/entity_metadata.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('An EntityMetadata should', () {
    test('support loading metadata from JSON', () {
      const fileMetadataJson = '''
        {
            "path": "/a/b/c",
            "link": "/d/e/f",
            "size": 1024,
            "is_hidden": false,
            "created": "2020-10-01T01:02:07",
            "updated": "2020-10-01T01:02:08",
            "owner": "test-user",
            "group": "test-group",
            "permissions": "004",
            "checksum": 42,
            "crates": {
                "/some/path/03_0": "some-uuid",
                "/some/path/03_1": "some-uuid",
                "/some/path/03_2": "some-uuid",
                "/some/path/03_3": "some-uuid",
                "/some/path/03_4": "some-uuid"
            },
            "compression": "none",
            "entity_type": "file"
        }
      ''';

      const directoryMetadataJson = '''
        {
            "path": "/a/b/c",
            "is_hidden": true,
            "created": "2020-10-01T01:02:09",
            "updated": "2020-10-01T01:02:10",
            "owner": "test-user",
            "group": "test-group",
            "permissions": "004",
            "entity_type": "directory"
        }
      ''';

      final expectedFileMetadata = FileEntityMetadata(
        path: '/a/b/c',
        link: '/d/e/f',
        isHidden: false,
        created: DateTime.parse('2020-10-01T01:02:07'),
        updated: DateTime.parse('2020-10-01T01:02:08'),
        owner: 'test-user',
        group: 'test-group',
        permissions: '004',
        size: 1024,
        checksum: 42,
        crates: {
          '/some/path/03_0': 'some-uuid',
          '/some/path/03_1': 'some-uuid',
          '/some/path/03_2': 'some-uuid',
          '/some/path/03_3': 'some-uuid',
          '/some/path/03_4': 'some-uuid',
        },
        compression: 'none',
        entityType: 'file',
      );

      final actualFileMetadata = EntityMetadata.fromJson(jsonDecode(fileMetadataJson));

      final expectedDirectoryMetadata = DirectoryEntityMetadata(
        path: '/a/b/c',
        link: null,
        isHidden: true,
        created: DateTime.parse('2020-10-01T01:02:09'),
        updated: DateTime.parse('2020-10-01T01:02:10'),
        owner: 'test-user',
        group: 'test-group',
        permissions: '004',
        entityType: 'directory',
      );

      final actualDirectoryMetadata = EntityMetadata.fromJson(jsonDecode(directoryMetadataJson));

      expect(expectedFileMetadata, actualFileMetadata);
      expect(expectedDirectoryMetadata, actualDirectoryMetadata);
    });

    test('fail to load metadata with invalid entity types', () {
      expect(() => EntityMetadata.fromJson(jsonDecode('{"entity_type":"other"}')), throwsArgumentError);
    });

    test('provide metadata extractor functions', () {
      final file = FileEntityMetadata(
        path: '/a/b/c/d',
        link: '/d/e/f',
        isHidden: false,
        created: DateTime.parse('2020-10-01T01:02:07'),
        updated: DateTime.parse('2020-10-01T01:02:08'),
        owner: 'test-user-1',
        group: 'test-group-1',
        permissions: '004',
        size: 1024,
        checksum: 42,
        crates: {
          '/some/path/03_0': 'some-uuid',
          '/some/path/03_1': 'some-uuid',
          '/some/path/03_2': 'some-uuid',
          '/some/path/03_3': 'some-uuid',
          '/some/path/03_4': 'some-uuid',
        },
        compression: 'none',
        entityType: 'file',
      );

      final directory = DirectoryEntityMetadata(
        path: '/a/b/c',
        link: null,
        isHidden: true,
        created: DateTime.parse('2020-10-01T01:02:09'),
        updated: DateTime.parse('2020-10-01T01:02:10'),
        owner: 'test-user-2',
        group: 'test-group-2',
        permissions: '005',
        entityType: 'directory',
      );

      final EntityMetadata fileAsEntityMetadata = file;
      expect(fileAsEntityMetadata.path, file.path);
      expect(fileAsEntityMetadata.link, file.link);
      expect(fileAsEntityMetadata.isHidden, file.isHidden);
      expect(fileAsEntityMetadata.created, file.created);
      expect(fileAsEntityMetadata.updated, file.updated);
      expect(fileAsEntityMetadata.owner, file.owner);
      expect(fileAsEntityMetadata.group, file.group);
      expect(fileAsEntityMetadata.permissions, file.permissions);
      expect(fileAsEntityMetadata.size, file.size);
      expect(fileAsEntityMetadata.crates, file.crates);
      expect(fileAsEntityMetadata.compression, file.compression);

      final EntityMetadata directoryAsEntityMetadata = directory;
      expect(directoryAsEntityMetadata.path, directory.path);
      expect(directoryAsEntityMetadata.link, directory.link);
      expect(directoryAsEntityMetadata.isHidden, directory.isHidden);
      expect(directoryAsEntityMetadata.created, directory.created);
      expect(directoryAsEntityMetadata.updated, directory.updated);
      expect(directoryAsEntityMetadata.owner, directory.owner);
      expect(directoryAsEntityMetadata.group, directory.group);
      expect(directoryAsEntityMetadata.permissions, directory.permissions);
      expect(directoryAsEntityMetadata.size, 0);
      expect(directoryAsEntityMetadata.crates, {});
      expect(directoryAsEntityMetadata.compression, 'none');
    });
  });
}
