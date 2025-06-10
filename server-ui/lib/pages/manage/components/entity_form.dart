import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/model/devices/device.dart';
import 'package:server_ui/model/users/permission.dart';
import 'package:server_ui/model/users/user.dart';
import 'package:server_ui/pages/manage/components/forms/boolean_field.dart';
import 'package:server_ui/pages/manage/components/forms/date_time_field.dart';
import 'package:server_ui/pages/manage/components/forms/device_limits_field.dart';
import 'package:server_ui/pages/manage/components/forms/duration_field.dart';
import 'package:server_ui/pages/manage/components/forms/password_field.dart';
import 'package:server_ui/pages/manage/components/forms/policy_field.dart';
import 'package:server_ui/pages/manage/components/forms/retention_field.dart';
import 'package:server_ui/pages/manage/components/forms/state_field.dart';
import 'package:server_ui/pages/manage/components/forms/user_limits_field.dart';
import 'package:server_ui/pages/manage/components/forms/user_permissions_field.dart';
import 'package:server_ui/utils/pair.dart';

class EntityForm extends StatefulWidget {
  const EntityForm({
    super.key,
    required this.fields,
    required this.submitAction,
    required this.onFormSubmitted,
  });

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

RetentionField retentionField({
  required String title,
  required void Function(Retention) onChange,
  Retention? initialRetention,
}) {
  return RetentionField(title: title, initialRetention: initialRetention, onChange: onChange);
}

DropdownButtonFormField dropdownField({
  required String title,
  required List<Pair<String, String>> items,
  String? errorMessage,
  required void Function(String) onFieldUpdated,
}) {
  return DropdownButtonFormField<String>(
    items: items.map((e) => DropdownMenuItem<String>(value: e.a, child: Text(e.b))).toList(),
    decoration: InputDecoration(
      labelText: title,
    ),
    onChanged: (value) => onFieldUpdated(value as String),
    validator: (value) => (value?.isEmpty ?? true) ? errorMessage : null,
  );
}

StateField stateField({
  required String title,
  required bool initialState,
  required void Function(bool) onChange,
}) {
  return StateField(title: title, initialState: initialState, onChange: onChange);
}

PolicyField policyField({
  required String title,
  required void Function(Policy) onChange,
  Policy? initialPolicy,
}) {
  return PolicyField(title: title, onChange: onChange, initialPolicy: initialPolicy);
}

DurationField durationField({
  required String title,
  required void Function(Duration) onChange,
  Duration? initialDuration,
  String? errorMessage,
}) {
  return DurationField(title: title, onChange: onChange, initialDuration: initialDuration, errorMessage: errorMessage);
}

DeviceLimitsField deviceLimitsField({
  required String title,
  required void Function(DeviceLimits?) onChange,
  DeviceLimits? initialDeviceLimits,
}) {
  return DeviceLimitsField(title: title, onChange: onChange, initialDeviceLimits: initialDeviceLimits);
}

UserLimitsField userLimitsField({
  required String title,
  required void Function(UserLimits?) onChange,
  UserLimits? initialUserLimits,
}) {
  return UserLimitsField(title: title, onChange: onChange, initialUserLimits: initialUserLimits);
}

UserPermissionsField userPermissionsField({
  required String title,
  required void Function(List<UserPermission>) onChange,
  List<UserPermission>? initialPermissions,
  String? errorMessage,
}) {
  return UserPermissionsField(
    title: title,
    onChange: onChange,
    initialPermissions: initialPermissions,
    errorMessage: errorMessage,
  );
}

PasswordField userPasswordField({
  required String title,
  required Function(String) onChange,
}) {
  return PasswordField(title: title, onChange: onChange);
}

DateTimeField dateTimeField({
  required String title,
  required void Function(DateTime) onChange,
  DateTime? initialDateTime,
  bool useExtendedTitle = true,
}) {
  return DateTimeField(
    title: title,
    onChange: onChange,
    initialDateTime: initialDateTime,
    useExtendedTitle: useExtendedTitle,
  );
}

BooleanField booleanField({
  required String title,
  required bool initialValue,
  required void Function(bool) onChange,
}) {
  return BooleanField(title: title, onChange: onChange, initialValue: initialValue);
}
