import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_entry.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/forms/date_time_field.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/triple.dart';
import 'package:flutter/material.dart';

enum DatasetEntryType {
  latest,
  entry,
  until;
}

class DatasetEntryController extends ValueNotifier<Triple<DatasetEntryType?, String?, DateTime>> {
  set entryType(DatasetEntryType? updated) =>
      value = Triple<DatasetEntryType?, String?, DateTime>(updated, value.b, value.c);

  set entry(String? updated) => value = Triple<DatasetEntryType?, String?, DateTime>(value.a, updated, value.c);

  set dateTime(DateTime updated) => value = Triple<DatasetEntryType?, String?, DateTime>(value.a, value.b, updated);

  DatasetEntryType? get entryType => value.a;

  String? get entry => value.b;

  DateTime get dateTime => value.c;

  void reset() => value = Triple<DatasetEntryType?, String?, DateTime>(
        DatasetEntryType.latest,
        null,
        DateTime.now(),
      );

  DatasetEntryController()
      : super(Triple<DatasetEntryType?, String?, DateTime>(
          DatasetEntryType.latest,
          null,
          DateTime.now(),
        ));
}

class DatasetEntryField extends StatefulWidget {
  const DatasetEntryField({
    super.key,
    required this.client,
    required this.definition,
    required this.controller,
  });

  final ClientApi client;
  final String definition;
  final DatasetEntryController controller;

  @override
  State createState() {
    return _DatasetEntryFieldState();
  }
}

class _DatasetEntryFieldState extends State<DatasetEntryField> {
  late final _dateTimeController = DateTimeController(initialValue: widget.controller.dateTime);

  @override
  void initState() {
    super.initState();
    _dateTimeController.addListener(() {
      widget.controller.dateTime = _dateTimeController.value;
    });
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<DatasetEntry>>(
      future: widget.client.getDatasetEntriesForDefinition(definition: widget.definition),
      builder: (context, snapshot) {
        if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
          final entries = snapshot.data!..sort((a, b) => b.created.compareTo(a.created));

          if (entries.isNotEmpty) {
            final entryTypeToggle = Row(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ToggleButtons(
                  isSelected: [
                    widget.controller.entryType == DatasetEntryType.latest,
                    widget.controller.entryType == DatasetEntryType.entry,
                    widget.controller.entryType == DatasetEntryType.until
                  ],
                  onPressed: (i) {
                    final selected = DatasetEntryType.values[i];
                    setState(() {
                      widget.controller.entryType = selected;
                    });
                  },
                  constraints: const BoxConstraints(minHeight: 32.0),
                  borderRadius: BorderRadius.circular(4.0),
                  children: const [
                    Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16.0),
                      child: Text('FROM LATEST'),
                    ),
                    Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16.0),
                      child: Text('FROM ENTRY'),
                    ),
                    Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16.0),
                      child: Text('UNTIL'),
                    ),
                  ],
                )
              ],
            );

            Widget? extraControl;
            switch (widget.controller.entryType) {
              case DatasetEntryType.entry:
                extraControl = Padding(
                  padding: const EdgeInsets.only(top: 8.0),
                  child: DropdownButtonFormField<String>(
                    decoration: const InputDecoration(labelText: 'Backup Entry'),
                    items: entries
                        .map((e) => DropdownMenuItem<String>(
                              value: e.id,
                              child: Text(
                                '(${e.created.renderAsDate()}, ${e.created.renderAsTime()}) (${e.id.toMinimizedString()})',
                              ),
                            ))
                        .toList(),
                    initialValue: widget.controller.entry,
                    onChanged: (value) {
                      widget.controller.entry = value;
                    },
                  ),
                );
                break;
              case DatasetEntryType.until:
                extraControl = DateTimeField(controller: _dateTimeController);
                break;
              default:
                extraControl = null;
                break;
            }

            return Column(
              children: extraControl != null ? [entryTypeToggle, extraControl] : [entryTypeToggle],
            );
          } else {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              widget.controller.entryType = null;
            });

            return const Row(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('No backup entries found for definition', style: TextStyle(fontWeight: FontWeight.bold))
              ],
            );
          }
        } else {
          return const Center(child: CircularProgressIndicator());
        }
      },
    );
  }
}
