import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/pages/manage/components/forms/file_size_field.dart';
import 'package:server_ui/utils/pair.dart';

class CrateStoreDescriptorField extends StatefulWidget {
  const CrateStoreDescriptorField({
    super.key,
    required this.onChange,
    this.initialValue,
  });

  final CrateStoreDescriptor? initialValue;
  final void Function(CrateStoreDescriptor) onChange;

  @override
  State createState() {
    return _CrateStoreDescriptorFieldState();
  }
}

class _CrateStoreDescriptorFieldState extends State<CrateStoreDescriptorField> {
  final List<Pair<String, String>> _backendTypes = [
    Pair('memory', 'Memory'),
    Pair('container', 'Container'),
    Pair('file', 'File'),
  ];

  late String _selectedBackend = widget.initialValue?.backendType() ?? _backendTypes[0].a;

  @override
  Widget build(BuildContext context) {
    final backendTypeInput = DropdownButtonFormField<String>(
      decoration: const InputDecoration(labelText: 'Backend Type'),
      initialValue: _selectedBackend,
      items: _backendTypes.map((e) => DropdownMenuItem(value: e.a, child: Text(e.b))).toList(),
      onChanged: (value) {
        if (value != null) {
          setState(() => _selectedBackend = value);
        }
      },
    );

    return Column(children: [backendTypeInput, _createField()]);
  }

  Widget _createField() {
    switch (_selectedBackend) {
      case 'memory':
        return StreamingMemoryBackendDescriptorField(
          onChange: (updated) => widget.onChange(updated),
          initialValue: widget.initialValue?.asMemory(),
        );
      case 'container':
        return ContainerBackendDescriptorField(
          onChange: (updated) => widget.onChange(updated),
          initialValue: widget.initialValue?.asContainer(),
        );
      case 'file':
        return FileBackendDescriptorField(
          onChange: (updated) => widget.onChange(updated),
          initialValue: widget.initialValue?.asFile(),
        );
      default:
        throw ArgumentError('Unexpected backend type encountered: [$_selectedBackend]');
    }
  }
}

class StreamingMemoryBackendDescriptorField extends StatefulWidget {
  const StreamingMemoryBackendDescriptorField({
    super.key,
    required this.onChange,
    this.initialValue,
  });

  final StreamingMemoryBackendDescriptor? initialValue;
  final void Function(StreamingMemoryBackendDescriptor) onChange;

  @override
  State createState() {
    return _StreamingMemoryBackendDescriptorFieldState();
  }
}

class _StreamingMemoryBackendDescriptorFieldState extends State<StreamingMemoryBackendDescriptorField> {
  final _maxChunkSizes = [4096, 8192, 16384, 32768, 65536].map((e) => Pair(e, '$e bytes')).toList();

  late String? _name = widget.initialValue?.name;
  late int? _maxSize = widget.initialValue?.maxSize;
  late int _maxChunkSize = widget.initialValue?.maxChunkSize ?? _maxChunkSizes[1].a;

  @override
  Widget build(BuildContext context) {
    final nameField = TextFormField(
      decoration: const InputDecoration(labelText: 'Name'),
      controller: TextEditingController(text: _name),
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A name is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty) {
          _name = value;
          if (_storeAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    final maxSizeField = FileSizeField(
      title: 'Maximum Size',
      onChange: (updated) {
        _maxSize = updated;
        if (_storeAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      initialFileSize: _maxSize,
    );

    final maxChunkSizeField = DropdownButtonFormField<int>(
      decoration: const InputDecoration(labelText: 'Maximum Chunk Size'),
      initialValue: _maxChunkSize,
      items: _maxChunkSizes.map((e) => DropdownMenuItem(value: e.a, child: Text(e.b))).toList(),
      onChanged: (updated) {
        _maxChunkSize = updated!;
        if (_storeAvailable()) {
          widget.onChange(_fromFields());
        }
      },
    );

    return Column(children: [nameField, maxSizeField, maxChunkSizeField]);
  }

  bool _storeAvailable() {
    return _name != null && _maxSize != null;
  }

  StreamingMemoryBackendDescriptor _fromFields() {
    return CrateStoreDescriptor.memory(
      maxSize: _maxSize!,
      maxChunkSize: _maxChunkSize,
      name: _name!,
    ) as StreamingMemoryBackendDescriptor;
  }
}

class ContainerBackendDescriptorField extends StatefulWidget {
  const ContainerBackendDescriptorField({
    super.key,
    required this.onChange,
    this.initialValue,
  });

  final ContainerBackendDescriptor? initialValue;
  final void Function(ContainerBackendDescriptor) onChange;

  @override
  State createState() {
    return _ContainerBackendDescriptorFieldState();
  }
}

class _ContainerBackendDescriptorFieldState extends State<ContainerBackendDescriptorField> {
  final _maxChunkSizes = [4096, 8192, 16384, 32768, 65536].map((e) => Pair(e, '$e bytes')).toList();

  late String? _path = widget.initialValue?.path;
  late int _maxChunkSize = widget.initialValue?.maxChunkSize ?? _maxChunkSizes[1].a;
  late int? _maxChunks = widget.initialValue?.maxChunks;

  @override
  Widget build(BuildContext context) {
    final pathField = TextFormField(
      decoration: const InputDecoration(labelText: 'Path'),
      controller: TextEditingController(text: _path),
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A path is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty) {
          _path = value;
          if (_storeAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    final maxChunkSizeField = DropdownButtonFormField<int>(
      decoration: const InputDecoration(labelText: 'Maximum Chunk Size'),
      initialValue: _maxChunkSize,
      items: _maxChunkSizes.map((e) => DropdownMenuItem(value: e.a, child: Text(e.b))).toList(),
      onChanged: (updated) {
        _maxChunkSize = updated!;
        if (_storeAvailable()) {
          widget.onChange(_fromFields());
        }
      },
    );

    final maxChunksField = TextFormField(
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      decoration: const InputDecoration(labelText: 'Maximum Chunks'),
      controller: TextEditingController(text: _maxChunks?.toString()),
      validator: (value) {
        final actualValue = int.tryParse(value ?? '');
        if (actualValue == null || actualValue <= 0 || actualValue > 2147483647) {
          return 'A valid number of chunks is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        final actualValue = int.tryParse(value);
        if (actualValue != null) {
          _maxChunks = actualValue;
          if (_storeAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    return Column(children: [pathField, maxChunksField, maxChunkSizeField]);
  }

  bool _storeAvailable() {
    return _path != null && _maxChunks != null;
  }

  ContainerBackendDescriptor _fromFields() {
    return CrateStoreDescriptor.container(
      path: _path!,
      maxChunkSize: _maxChunkSize,
      maxChunks: _maxChunks!,
    ) as ContainerBackendDescriptor;
  }
}

class FileBackendDescriptorField extends StatefulWidget {
  const FileBackendDescriptorField({
    super.key,
    required this.onChange,
    this.initialValue,
  });

  final FileBackendDescriptor? initialValue;
  final void Function(FileBackendDescriptor) onChange;

  @override
  State createState() {
    return _FileBackendDescriptorFieldState();
  }
}

class _FileBackendDescriptorFieldState extends State<FileBackendDescriptorField> {
  late String? _parentDirectory = widget.initialValue?.parentDirectory;

  @override
  Widget build(BuildContext context) {
    final parentDirectoryField = TextFormField(
      decoration: const InputDecoration(labelText: 'Parent Directory'),
      controller: TextEditingController(text: _parentDirectory),
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A parent directory is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty) {
          _parentDirectory = value;
          if (_storeAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    return Column(children: [parentDirectoryField]);
  }

  bool _storeAvailable() {
    return _parentDirectory != null;
  }

  FileBackendDescriptor _fromFields() {
    return CrateStoreDescriptor.file(
      parentDirectory: _parentDirectory!,
    ) as FileBackendDescriptor;
  }
}
