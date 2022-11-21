import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/model/users/user.dart';
import 'package:server_ui/pages/manage/components/forms/duration_field.dart';
import 'package:server_ui/pages/manage/components/forms/file_size_field.dart';

class _UserLimitsFieldState extends State<UserLimitsField> {
  late bool _enabled = widget.initialUserLimits != null;
  late int? _maxDevices = widget.initialUserLimits?.maxDevices;
  late int? _maxCrates = widget.initialUserLimits?.maxCrates;
  late int? _maxStorage = widget.initialUserLimits?.maxStorage;
  late int? _maxStoragePerCrate = widget.initialUserLimits?.maxStoragePerCrate;
  late Duration? _maxRetention = widget.initialUserLimits?.maxRetention;
  late Duration? _minRetention = widget.initialUserLimits?.minRetention;

  @override
  Widget build(BuildContext context) {
    final enableLimitsSwitch = ListTile(
      title: Text('Enable ${widget.title}'),
      trailing: Switch(
        value: _enabled,
        activeColor: Theme.of(context).colorScheme.primary,
        onChanged: (value) {
          setState(() => _enabled = value);
          if (_limitsAvailable()) {
            widget.onChange(_fromFields());
          }
        },
      ),
    );

    final maxDevicesField = TextFormField(
      decoration: const InputDecoration(labelText: 'Maximum Devices'),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      controller: TextEditingController(text: _maxDevices?.toString()),
      validator: (value) {
        final actualValue = int.tryParse(value ?? '');
        if (actualValue == null || actualValue <= 0) {
          return 'A valid maximum number of devices is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        final actualValue = int.tryParse(value);
        if (actualValue != null) {
          _maxDevices = actualValue;
          if (_limitsAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
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

    final minRetentionInput = DurationField(
      title: 'Minimum Retention',
      onChange: (updated) {
        _minRetention = updated;
        if (_limitsAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      errorMessage: 'A valid duration is required',
      initialDuration: _minRetention,
    );

    final maxRetentionInput = DurationField(
      title: 'Maximum Retention',
      onChange: (updated) {
        _maxRetention = updated;
        if (_limitsAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      errorMessage: 'A valid duration is required',
      initialDuration: _maxRetention,
    );

    final List<Widget> limitsSwitch = [
      Row(children: [Expanded(child: enableLimitsSwitch)])
    ];

    final List<Widget> fields = _enabled
        ? [
            maxDevicesField,
            maxCratesField,
            maxStorageField,
            maxStoragePerCrateField,
            minRetentionInput,
            maxRetentionInput,
          ]
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

  UserLimits? _fromFields() {
    if (_enabled) {
      return UserLimits(
        maxDevices: _maxDevices!,
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

class UserLimitsField extends StatefulWidget {
  const UserLimitsField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialUserLimits,
  });

  final String title;
  final UserLimits? initialUserLimits;
  final void Function(UserLimits?) onChange;

  @override
  State createState() {
    return _UserLimitsFieldState();
  }
}
