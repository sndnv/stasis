import 'package:flutter/material.dart';

class PasswordField extends StatefulWidget {
  const PasswordField({
    super.key,
    required this.title,
    required this.onChange,
  });

  final String title;
  final void Function(String) onChange;

  @override
  State createState() {
    return _PasswordFieldState();
  }
}

class _PasswordFieldState extends State<PasswordField> {
  @override
  Widget build(BuildContext context) {
    final passwordInputController = TextEditingController();
    final passwordConfirmationInputController = TextEditingController();

    final passwordInput = TextFormField(
      keyboardType: TextInputType.text,
      decoration: InputDecoration(labelText: widget.title),
      controller: passwordInputController,
      obscureText: true,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A password needs to be provided';
        } else if (passwordConfirmationInputController.text != value) {
          return 'The provided passwords do not match';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty && passwordConfirmationInputController.text == value) {
          widget.onChange(value);
        }
      },
    );

    final passwordConfirmationInput = TextFormField(
      keyboardType: TextInputType.text,
      decoration: InputDecoration(labelText: '${widget.title} (confirm)'),
      controller: passwordConfirmationInputController,
      obscureText: true,
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A password needs to be provided';
        } else if (passwordInputController.text != value) {
          return 'The provided passwords do not match';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty && passwordInputController.text == value) {
          widget.onChange(value);
        }
      },
    );

    return Column(
      children: [
        passwordInput,
        const Padding(padding: EdgeInsets.symmetric(vertical: 8.0)),
        passwordConfirmationInput,
      ],
    );
  }
}
