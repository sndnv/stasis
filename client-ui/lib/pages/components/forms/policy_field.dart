import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class PolicyField extends StatefulWidget {
  const PolicyField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialPolicy,
  });

  final String title;
  final Policy? initialPolicy;
  final void Function(Policy) onChange;

  @override
  State createState() {
    return _PolicyFieldState();
  }
}

class _PolicyFieldState extends State<PolicyField> {
  final _policyTypes = [Pair('at-most', 'At Most'), Pair('latest-only', 'Latest-only'), Pair('all', 'All')];
  late String _policyType = widget.initialPolicy?.policyType ?? 'at-most';
  late int? _policyVersions = widget.initialPolicy?.versions;

  late bool _showPolicyVersions = _policyType == 'at-most';
  bool _policyVersionsInvalid = false;

  @override
  Widget build(BuildContext context) {
    final policyTypeInput = DropdownButtonFormField<String>(
      decoration: InputDecoration(labelText: widget.title, errorText: _policyVersionsInvalid ? '' : null),
      items: _policyTypes.map((e) => DropdownMenuItem<String>(value: e.a, child: Text(e.b))).toList(),
      value: _policyType,
      onChanged: (value) {
        _policyType = value!;
        if (_policyVersions != null || _policyType != 'at-most') {
          widget.onChange(_fromFields(_policyType, _policyVersions));
        }
        setState(() {
          if (_policyType != 'at-most') {
            _policyVersionsInvalid = false;
          }
          _showPolicyVersions = value == 'at-most';
        });
      },
    );

    final policyVersionsInput = TextFormField(
      decoration: const InputDecoration(labelText: 'Versions'),
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      controller: TextEditingController(text: _policyVersions?.toString()),
      validator: (value) {
        final actualValue = int.tryParse(value ?? '');
        if (_showPolicyVersions && (actualValue == null || actualValue <= 0)) {
          setState(() {
            _policyVersions = null;
            _policyVersionsInvalid = true;
          });
          return 'Required';
        } else {
          setState(() => _policyVersionsInvalid = false);
          return null;
        }
      },
      onChanged: (value) {
        final actualValue = int.tryParse(value);
        if (actualValue != null) {
          _policyVersions = actualValue;
          widget.onChange(_fromFields(_policyType, _policyVersions!));
        }
      },
    );

    final List<Widget> policyTypeWidgets = [Expanded(flex: 4, child: policyTypeInput)];

    final List<Widget> versionWidgets = _showPolicyVersions
        ? [const Padding(padding: EdgeInsets.only(right: 4.0)), Expanded(child: policyVersionsInput)]
        : [];

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: policyTypeWidgets + versionWidgets,
    );
  }

  Policy _fromFields(String policyType, int? versions) {
    return Policy(policyType: policyType, versions: versions);
  }
}
