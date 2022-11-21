import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';

void main() {
  group('A CrateStoreDescriptor should', () {
    test('support creating descriptors', () {
      const expectedMemory = StreamingMemoryBackendDescriptor(
        backendType: 'memory',
        maxSize: 1,
        maxChunkSize: 2,
        name: 'test-name',
      );

      final actualMemory = CrateStoreDescriptor.memory(
        maxSize: 1,
        maxChunkSize: 2,
        name: 'test-name',
      );

      const expectedContainer = ContainerBackendDescriptor(
        backendType: 'container',
        path: '/some/path',
        maxChunkSize: 1,
        maxChunks: 2,
      );

      final actualContainer = CrateStoreDescriptor.container(
        path: '/some/path',
        maxChunkSize: 1,
        maxChunks: 2,
      );

      const expectedFile = FileBackendDescriptor(
        backendType: 'file',
        parentDirectory: '/some/path',
      );

      final actualFile = CrateStoreDescriptor.file(
        parentDirectory: '/some/path',
      );

      expect(actualMemory, expectedMemory);
      expect(actualContainer, expectedContainer);
      expect(actualFile, expectedFile);
    });

    test('support loading descriptors from JSON', () {
      const memoryJson = '{"backend_type":"memory","max_size":42,"max_chunk_size":999,"name":"backend-format-test"}';
      const containerJson = '{"backend_type":"container","path":"/some/path","max_chunk_size":1,"max_chunks":2}';
      const fileJson = '{"backend_type":"file","parent_directory":"/some/path"}';

      final expectedMemory = CrateStoreDescriptor.memory(
        maxSize: 42,
        maxChunkSize: 999,
        name: 'backend-format-test',
      );

      final expectedContainer = CrateStoreDescriptor.container(
        path: '/some/path',
        maxChunkSize: 1,
        maxChunks: 2,
      );

      final expectedFile = CrateStoreDescriptor.file(
        parentDirectory: '/some/path',
      );

      final actualMemory = CrateStoreDescriptor.fromJson(jsonDecode(memoryJson));
      final actualContainer = CrateStoreDescriptor.fromJson(jsonDecode(containerJson));
      final actualFile = CrateStoreDescriptor.fromJson(jsonDecode(fileJson));

      expect(actualMemory, expectedMemory);
      expect(actualContainer, expectedContainer);
      expect(actualFile, expectedFile);
    });

    test('fail to load descriptors with invalid backend types', () {
      expect(() => CrateStoreDescriptor.fromJson(jsonDecode('{"backend_type":"other"}')), throwsArgumentError);
    });

    test('support extracting its backend type', () {
      const CrateStoreDescriptor memory = StreamingMemoryBackendDescriptor(
        backendType: 'memory',
        maxSize: 1,
        maxChunkSize: 2,
        name: 'test-name',
      );

      const CrateStoreDescriptor container = ContainerBackendDescriptor(
        backendType: 'container',
        path: '/some/path',
        maxChunkSize: 1,
        maxChunks: 2,
      );

      const CrateStoreDescriptor file = FileBackendDescriptor(
        backendType: 'file',
        parentDirectory: '/some/path',
      );

      expect(memory.backendType(), 'memory');
      expect(container.backendType(), 'container');
      expect(file.backendType(), 'file');
    });

    test('support extracting its location', () {
      const CrateStoreDescriptor memory = StreamingMemoryBackendDescriptor(
        backendType: 'memory',
        maxSize: 1,
        maxChunkSize: 2,
        name: 'test-name',
      );

      const CrateStoreDescriptor container = ContainerBackendDescriptor(
        backendType: 'container',
        path: '/some/path1',
        maxChunkSize: 1,
        maxChunks: 2,
      );

      const CrateStoreDescriptor file = FileBackendDescriptor(
        backendType: 'file',
        parentDirectory: '/some/path2',
      );

      expect(memory.location(), 'memory');
      expect(container.location(), '/some/path1');
      expect(file.location(), '/some/path2');
    });

    test('support conversion to specific types', () {
      const memory = StreamingMemoryBackendDescriptor(
        backendType: 'memory',
        maxSize: 1,
        maxChunkSize: 2,
        name: 'test-name',
      );

      const container = ContainerBackendDescriptor(
        backendType: 'container',
        path: '/some/path',
        maxChunkSize: 1,
        maxChunks: 2,
      );

      const file = FileBackendDescriptor(
        backendType: 'file',
        parentDirectory: '/some/path',
      );

      expect(memory.asMemory(), memory);
      expect(memory.asContainer(), null);
      expect(memory.asFile(), null);

      expect(container.asMemory(), null);
      expect(container.asContainer(), container);
      expect(container.asFile(), null);

      expect(file.asMemory(), null);
      expect(file.asContainer(), null);
      expect(file.asFile(), file);
    });
  });
}
