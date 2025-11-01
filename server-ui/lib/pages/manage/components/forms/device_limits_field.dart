import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/model/devices/device.dart';
import 'package:server_ui/pages/manage/components/forms/duration_field.dart';
import 'package:server_ui/pages/manage/components/forms/file_size_field.dart';

class DeviceLimitsField extends StatefulWidget {
  const DeviceLimitsField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialDeviceLimits,
  });

  final String title;
  final DeviceLimits? initialDeviceLimits;
  final void Function(DeviceLimits?) onChange;

  @override
  State createState() {
    return _DeviceLimitsFieldState();
  }
}

class _DeviceLimitsFieldState extends State<DeviceLimitsField> {
  late bool _enabled = widget.initialDeviceLimits != null;
  late int? _maxCrates = widget.initialDeviceLimits?.maxCrates;
  late int? _maxStorage = widget.initialDeviceLimits?.maxStorage;
  late int? _maxStoragePerCrate = widget.initialDeviceLimits?.maxStoragePerCrate;
  late Duration? _maxRetention = widget.initialDeviceLimits?.maxRetention;
  late Duration? _minRetention = widget.initialDeviceLimits?.minRetention;

  @override
  Widget build(BuildContext context) {
    final enableLimitsSwitch = ListTile(
      title: Text('Enable ${widget.title}'),
      trailing: Switch(
        value: _enabled,
        activeThumbColor: Theme.of(context).colorScheme.primary,
        onChanged: (value) {
          setState(() => _enabled = value);
          if (_limitsAvailable()) {
            widget.onChange(_fromFields());
          }
        },
      ),
    );

    final maxCratesField = TextFormField(
      decoration: const InputDecoration(labelText: 'Maximum Crates'),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      controller: TextEditingController(text: _maxCrates?.toString()),
      validator: (value) {
        final actualValue = int.tryParse(value ?? '');
        if (actualValue == null || actualValue <= 0) {
          return 'A valid maximum number of crates is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        final actualValue = int.tryParse(value);
        if (actualValue != null) {
          _maxCrates = actualValue;
          if (_limitsAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    final maxStorageField = FileSizeField(
      title: 'Maximum Storage',
      onChange: (value) {
        _maxStorage = value;
        if (_limitsAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      initialFileSize: _maxStorage,
      errorMessage: 'A valid storage size is required',
    );

    final maxStoragePerCrateField = FileSizeField(
      title: 'Maximum Storage per Crate',
      onChange: (value) {
        _maxStoragePerCrate = value;
        if (_limitsAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      initialFileSize: _maxStoragePerCrate,
      errorMessage: 'A valid storage size is required',
    );

    final maxRetentionInput = DurationField(
      title: 'Maximum Duration',
      onChange: (updated) {
        _maxRetention = updated;
        if (_limitsAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      errorMessage: 'A valid duration is required',
      initialDuration: _maxRetention,
    );

    final minRetentionInput = DurationField(
      title: 'Minimum Duration',
      onChange: (updated) {
        _minRetention = updated;
        if (_limitsAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      errorMessage: 'A valid duration is required',
      initialDuration: _minRetention,
    );

    final List<Widget> limitsSwitch = [
      Row(children: [Expanded(child: enableLimitsSwitch)])
    ];

    final List<Widget> fields = _enabled
        ? [maxCratesField, maxStorageField, maxStoragePerCrateField, minRetentionInput, maxRetentionInput]
        : [];

    return Column(
      children: limitsSwitch + fields,
    );
  }

  bool _limitsAvailable() {
    if (_enabled) {
      return _maxCrates != null &&
          _maxStorage != null &&
          _maxStoragePerCrate != null &&
          _maxRetention != null &&
          _minRetention != null;
    } else {
      return true;
    }
  }

  DeviceLimits? _fromFields() {
    if (_enabled) {
      return DeviceLimits(
        maxCrates: _maxCrates!,
        maxStorage: _maxStorage!,
        maxStoragePerCrate: _maxStoragePerCrate!,
        maxRetention: _maxRetention!,
        minRetention: _minRetention!,
      );
    } else {
      return null;
    }
  }
}
