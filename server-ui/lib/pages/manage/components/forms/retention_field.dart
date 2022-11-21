import 'package:flutter/material.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/pages/manage/components/forms/duration_field.dart';
import 'package:server_ui/pages/manage/components/forms/policy_field.dart';

class RetentionField extends StatefulWidget {
  const RetentionField({super.key, required this.title, required this.onChange, this.initialRetention});

  final String title;
  final Retention? initialRetention;
  final void Function(Retention) onChange;

  @override
  State createState() {
    return _RetentionFieldState();
  }
}

class _RetentionFieldState extends State<RetentionField> {
  late Duration? _duration = widget.initialRetention?.duration;
  late Policy? _policy = widget.initialRetention?.policy;

  @override
  Widget build(BuildContext context) {
    final durationInput = DurationField(
      title: 'Duration',
      onChange: (updated) {
        _duration = updated;
        if (_policy != null) {
          widget.onChange(_fromFields(_duration!, _policy!));
        }
      },
      errorMessage: 'Valid retention duration is required',
      initialDuration: _duration,
    );

    final policyInput = PolicyField(
      title: 'Policy',
      onChange: (updated) {
        _policy = updated;
        if (_duration != null) {
          widget.onChange(_fromFields(_duration!, _policy!));
        }
      },
      initialPolicy: _policy,
    );

    return Column(
      children: [
        Row(children: [Text(widget.title, style: Theme.of(context).textTheme.bodyMedium, textAlign: TextAlign.left)]),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 10.0), child: durationInput),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 10.0), child: policyInput),
      ],
    );
  }

  Retention _fromFields(Duration duration, Policy policy) {
    return Retention(policy: policy, duration: duration);
  }
}
