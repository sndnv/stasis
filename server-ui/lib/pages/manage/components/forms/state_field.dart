import 'package:flutter/material.dart';

class StateField extends StatefulWidget {
  const StateField({super.key, required this.title, required this.initialState, required this.onChange});

  final String title;
  final bool initialState;
  final void Function(bool) onChange;

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
          widget.onChange(updatedState);
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
