import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/file_size_unit.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class FileSizeField extends StatefulWidget {
  const FileSizeField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialFileSize,
    this.errorMessage,
  });

  final String title;
  final int? initialFileSize;
  final void Function(int) onChange;
  final String? errorMessage;

  @override
  State createState() {
    return _FileSizeFieldState();
  }
}

class _FileSizeFieldState extends State<FileSizeField> {
  bool _amountInvalid = false;

  final _units = [
    FileSizeUnit.bytes,
    FileSizeUnit.kilobytes,
    FileSizeUnit.megabytes,
    FileSizeUnit.gigabytes,
    FileSizeUnit.terabytes,
    FileSizeUnit.petabytes,
  ];

  late final _initialFields = widget.initialFileSize?.toFields();
  late int? _amount = _initialFields?.a;
  late FileSizeUnit _unit = _initialFields?.b ?? FileSizeUnit.gigabytes;

  @override
  Widget build(BuildContext context) {
    final fileSizeAmountInput = TextFormField(
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      decoration: InputDecoration(labelText: widget.title),
      controller: TextEditingController(text: _amount?.toString()),
      validator: (value) {
        final actualValue = int.tryParse(value ?? '');
        if (widget.errorMessage != null && (actualValue == null || actualValue <= 0)) {
          setState(() {
            _amount = null;
            _amountInvalid = true;
          });
          return widget.errorMessage;
        } else {
          setState(() => _amountInvalid = false);
          return null;
        }
      },
      onChanged: (value) {
        final actualValue = int.tryParse(value);
        if (actualValue != null) {
          _amount = actualValue;
          widget.onChange(_fromFields(_amount!, _unit));
        }
      },
    );

    final fileSizeUnitInput = DropdownButtonFormField<FileSizeUnit>(
      decoration: InputDecoration(errorText: _amountInvalid ? '' : null),
      value: _unit,
      items: _units.map((e) => DropdownMenuItem<FileSizeUnit>(value: e, child: Text(e.symbol))).toList(),
      onChanged: (value) {
        _unit = value!;
        if (_amount != null) {
          widget.onChange(_fromFields(_amount!, _unit));
        }
      },
    );

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(flex: 3, child: fileSizeAmountInput),
        const Padding(padding: EdgeInsets.only(right: 4.0)),
        Expanded(child: fileSizeUnitInput),
      ],
    );
  }

  int _fromFields(int amount, FileSizeUnit unit) {
    const base = 1000;

    switch (unit) {
      case FileSizeUnit.bytes:
        return amount;
      case FileSizeUnit.kilobytes:
        return amount * base;
      case FileSizeUnit.megabytes:
        return amount * base * base;
      case FileSizeUnit.gigabytes:
        return amount * base * base * base;
      case FileSizeUnit.terabytes:
        return amount * base * base * base * base;
      case FileSizeUnit.petabytes:
        return amount * base * base * base * base * base;
    }
  }
}
