import 'package:flutter/material.dart';

class BooleanField extends StatefulWidget {
  const BooleanField({
    super.key,
    required this.title,
    required this.onChange,
    required this.initialValue,
  });

  final String title;
  final bool initialValue;
  final void Function(bool) onChange;

  @override
  State createState() {
    return _BooleanFieldState();
  }
}

class _BooleanFieldState extends State<BooleanField> {
  late bool _value = widget.initialValue;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: Text(widget.title),
      trailing: Switch(
        value: _value,
        activeThumbColor: Theme.of(context).colorScheme.primary,
        onChanged: (value) {
          setState(() => _value = value);
          widget.onChange(_value);
        },
      ),
    );
  }
}
