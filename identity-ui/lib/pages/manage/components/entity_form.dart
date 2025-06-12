import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class EntityForm extends StatefulWidget {
  const EntityForm({super.key, required this.fields, required this.submitAction, required this.onFormSubmitted});

  final List<Widget> fields;
  final String submitAction;
  final void Function() onFormSubmitted;

  @override
  State createState() {
    return _EntityFormState();
  }
}

class _EntityFormState extends State<EntityForm> {
  final _key = GlobalKey<FormState>();
  bool _processingResponse = false;

  @override
  Widget build(BuildContext context) {
    final cancelButton = TextButton(
      onPressed: _processingResponse ? null : _cancelHandler,
      child: const Text('Cancel'),
    );

    final submitButton = ElevatedButton(
      onPressed: _processingResponse ? null : _submitHandler,
      child: Text(widget.submitAction),
    );

    Widget buttons;
    if (_processingResponse) {
      buttons = const CircularProgressIndicator();
    } else {
      buttons = Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [cancelButton, const Padding(padding: EdgeInsets.only(left: 8.0, right: 8.0)), submitButton],
      );
    }

    return Form(
      key: _key,
      child: SizedBox(
        width: 480,
        child: Column(
          children: (widget.fields.map<Widget>((e) => SelectionArea(child: e)).toList() + [buttons])
              .map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e))
              .toList(),
        ),
      ),
    );
  }

  void _cancelHandler() {
    Navigator.pop(context);
  }

  void _submitHandler() {
    _processing(true);

    if (_key.currentState?.validate() ?? false) {
      widget.onFormSubmitted();
    } else {
      _processing(false);
    }
  }

  void _processing(bool value) {
    setState(() {
      _processingResponse = value;
    });
  }
}

TextFormField formField({
  required String title,
  required TextEditingController controller,
  String? errorMessage,
  TextInputType? type,
  bool? secret,
}) {
  return TextFormField(
    keyboardType: type ?? TextInputType.text,
    inputFormatters: type == TextInputType.number ? [FilteringTextInputFormatter.digitsOnly] : [],
    decoration: InputDecoration(
      labelText: title,
    ),
    controller: controller,
    obscureText: secret ?? false,
    validator: (value) {
      final actualValue = (secret ?? false) ? value : value?.toString().trim();

      if (errorMessage != null && (actualValue == null || actualValue.isEmpty)) {
        return errorMessage;
      } else {
        return null;
      }
    },
  );
}

StateField stateField(
    {required String title, required bool initialState, required void Function(bool) onStateUpdated}) {
  return StateField(title: title, initialState: initialState, onStateUpdated: onStateUpdated);
}

class StateField extends StatefulWidget {
  const StateField({super.key, required this.title, required this.initialState, required this.onStateUpdated});

  final String title;
  final bool initialState;
  final void Function(bool) onStateUpdated;

  @override
  State createState() {
    return _StateFieldState();
  }
}

class _StateFieldState extends State<StateField> {
  static const int _activeButtonId = 0;

  bool? _currentState;

  bool get currentState {
    return _currentState ?? widget.initialState;
  }

  @override
  Widget build(BuildContext context) {
    final buttons = ToggleButtons(
      isSelected: (_currentState ?? widget.initialState) ? const [true, false] : const [false, true],
      onPressed: (pressedButtonId) {
        setState(() {
          final updatedState = pressedButtonId == _activeButtonId;
          widget.onStateUpdated(updatedState);
          _currentState = updatedState;
        });
      },
      children: const [
        Padding(
          padding: EdgeInsets.all(4.0),
          child: Text('Active'),
        ),
        Padding(
          padding: EdgeInsets.all(4.0),
          child: Text('Inactive'),
        ),
      ],
    );

    return Row(
      mainAxisAlignment: MainAxisAlignment.start,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.title,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            SizedBox(height: 36, child: buttons)
          ],
        ),
      ],
    );
  }
}
