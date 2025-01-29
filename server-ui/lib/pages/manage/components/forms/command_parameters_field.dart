import 'package:flutter/material.dart';
import 'package:server_ui/model/commands/command.dart';
import 'package:server_ui/utils/pair.dart';

class _CommandParametersFieldState extends State<CommandParametersField> {
  String? _commandType;
  String? _logoutReason;

  final List<Pair<String, String>> commandTypes = [
    Pair('logout_user', 'Logout User'),
  ];

  @override
  Widget build(BuildContext context) {
    final commandTypeField = DropdownButtonFormField<String>(
      items: commandTypes.map((e) => DropdownMenuItem<String>(value: e.a, child: Text(e.b))).toList(),
      decoration: InputDecoration(
        labelText: 'Command Type',
      ),
      onChanged: (value) {
        setState(() => _commandType = (value as String));
        if (_parametersAvailable()) {
          widget.onChange(_fromFields());
        }
      },
      validator: (value) => (value?.isEmpty ?? true) ? 'A valid command type must be selected' : null,
    );

    final logoutUserReasonField = TextFormField(
      decoration: const InputDecoration(labelText: 'Logout Reason'),
      keyboardType: TextInputType.text,
      controller: TextEditingController(),
      onChanged: (value) {
        final actualValue = value.trim();
        if (actualValue.isNotEmpty) {
          _logoutReason = actualValue;
          if (_parametersAvailable()) {
            widget.onChange(_fromFields());
          }
        } else {
          _logoutReason = null;
        }
      },
    );

    final List<Widget> commandTypeFieldRow = [
      Row(children: [Expanded(child: commandTypeField)])
    ];

    final List<Widget> fields = _commandType == 'logout_user' ? [logoutUserReasonField] : [];

    return Column(
      children: commandTypeFieldRow + fields,
    );
  }

  bool _parametersAvailable() {
    return _commandType == 'logout_user';
  }

  CommandParameters? _fromFields() {
    final type = _commandType;

    return type != null
        ? CommandParameters(
            commandType: type,
            logoutUser: LogoutUserCommand(reason: _logoutReason),
          )
        : null;
  }
}

class CommandParametersField extends StatefulWidget {
  const CommandParametersField({
    super.key,
    required this.onChange,
  });

  final void Function(CommandParameters?) onChange;

  @override
  State createState() {
    return _CommandParametersFieldState();
  }
}
