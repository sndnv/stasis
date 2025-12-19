import 'package:flutter/material.dart';

class BooleanController extends ValueNotifier<bool> {
  BooleanController({required bool initialValue}) : super(initialValue);
}

class BooleanField extends StatefulWidget {
  const BooleanField({super.key, required this.title, required this.controller, this.hint, this.enabled = false});

  final String title;
  final String? hint;
  final bool enabled;
  final BooleanController controller;

  @override
  State createState() {
    return _BooleanFieldState();
  }
}

class _BooleanFieldState extends State<BooleanField> {
  late bool _value = widget.controller.value;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: Align(
        alignment: AlignmentGeometry.centerLeft,
        child: FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            widget.title,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
      ),
      leading: widget.hint != null
          ? Tooltip(
              message: widget.hint,
              child: Icon(Icons.help_center),
            )
          : null,
      trailing: Switch(
        value: _value,
        activeThumbColor: Theme.of(context).colorScheme.primary,
        onChanged: widget.enabled
            ? (value) {
                setState(() => _value = value);
                widget.controller.value = value;
              }
            : null,
      ),
    );
  }
}
