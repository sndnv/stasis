import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/chrono_unit.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class DurationField extends StatefulWidget {
  const DurationField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialDuration,
    this.errorMessage,
  });

  final String title;
  final Duration? initialDuration;
  final void Function(Duration) onChange;
  final String? errorMessage;

  @override
  State createState() {
    return _DurationFieldState();
  }
}

class _DurationFieldState extends State<DurationField> {
  bool _amountInvalid = false;

  final _units = [ChronoUnit.days, ChronoUnit.hours, ChronoUnit.minutes, ChronoUnit.seconds];

  late final _initialFields = widget.initialDuration?.toFields();
  late int? _amount = _initialFields?.a;
  late ChronoUnit _unit = _initialFields?.b ?? ChronoUnit.days;

  @override
  Widget build(BuildContext context) {
    final durationAmountInput = TextFormField(
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

    final durationUnitInput = DropdownButtonFormField<ChronoUnit>(
      decoration: InputDecoration(errorText: _amountInvalid ? '' : null),
      value: _unit,
      items: _units.map((e) => DropdownMenuItem<ChronoUnit>(value: e, child: Text(e.plural))).toList(),
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
        Expanded(flex: 3, child: durationAmountInput),
        const Padding(padding: EdgeInsets.only(right: 4.0)),
        Expanded(child: durationUnitInput),
      ],
    );
  }

  Duration _fromFields(int amount, ChronoUnit unit) {
    switch (unit) {
      case ChronoUnit.days:
        return Duration(days: amount);
      case ChronoUnit.hours:
        return Duration(hours: amount);
      case ChronoUnit.minutes:
        return Duration(minutes: amount);
      case ChronoUnit.seconds:
        return Duration(seconds: amount);
    }
  }
}
