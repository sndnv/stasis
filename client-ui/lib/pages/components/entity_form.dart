import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/pages/components/forms/boolean_field.dart';
import 'package:stasis_client_ui/pages/components/forms/dataset_entry_field.dart';
import 'package:stasis_client_ui/pages/components/forms/date_time_field.dart';
import 'package:stasis_client_ui/pages/components/forms/duration_field.dart';
import 'package:stasis_client_ui/pages/components/forms/policy_field.dart';
import 'package:stasis_client_ui/pages/components/forms/retention_field.dart';
import 'package:stasis_client_ui/utils/pair.dart';

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
        children: [
          cancelButton,
          const Padding(padding: EdgeInsets.only(left: 8.0, right: 8.0)),
          submitButton,
        ],
      );
    }

    return Form(
      key: _key,
      child: SizedBox(
        width: 480,
        child: Column(
          children: (widget.fields + [buttons])
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
  required void Function(String) onFieldUpdated,
  String? errorMessage,
  String? selected,
  bool outlined = false,
}) {
  return DropdownButtonFormField<String>(
    items: items
        .map(
          (e) => DropdownMenuItem<String>(
            value: e.a,
            child: Text(e.b),
          ),
        )
        .toList(),
    initialValue: selected,
    decoration: InputDecoration(
      labelText: title,
      border: outlined ? const OutlineInputBorder() : null,
    ),
    onChanged: (value) => onFieldUpdated(value as String),
    validator: (value) => (value?.isEmpty ?? true) ? errorMessage : null,
  );
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

DateTimeField dateTimeField({
  required String title,
  required DateTimeController controller,
}) {
  return DateTimeField(title: title, controller: controller);
}

BooleanField booleanField({
  required String title,
  required BooleanController controller,
  String? hint,
  bool enabled = false,
}) {
  return BooleanField(title: title, hint: hint, controller: controller, enabled: enabled);
}

DatasetEntryField datasetEntryField({
  required ClientApi client,
  required String definition,
  required DatasetEntryController controller,
}) {
  return DatasetEntryField(client: client, definition: definition, controller: controller);
}
