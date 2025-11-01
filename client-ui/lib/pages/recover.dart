import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/api/responses/operation_started.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/entity_form.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/forms/dataset_entry_field.dart';

class Recover extends StatefulWidget {
  const Recover({
    super.key,
    required this.client,
  });

  final ClientApi client;

  @override
  State createState() {
    return _RecoverState();
  }
}

class _RecoverState extends State<Recover> {
  String? _selectedDefinition;

  final TextEditingController _definitionController = TextEditingController();
  final DatasetEntryController _entryController = DatasetEntryController();
  final TextEditingController _queryController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return buildPage<List<DatasetDefinition>>(
      of: () => widget.client.getDatasetDefinitions(),
      builder: (context, definitions) {
        final definitionCard = card(
          child: DropdownButtonFormField<String>(
            items: definitions
                .map((d) =>
                    DropdownMenuItem<String>(value: d.id, child: Text('${d.info} (${d.id.toMinimizedString()})')))
                .toList(),
            initialValue: _selectedDefinition,
            decoration: const InputDecoration(labelText: 'Backup Definition'),
            onChanged: (value) {
              setState(() {
                final actualValue = value as String;
                _selectedDefinition = actualValue;
                _definitionController.value = TextEditingValue(text: actualValue);
                _entryController.reset();
              });
            },
          ),
          title: 'Start the recovery process of a dataset',
        );

        final submitButton = Padding(
          padding: const EdgeInsets.only(bottom: 24.0),
          child: RecoverButton(
            definitionController: _definitionController,
            entryController: _entryController,
            onPressed: () {
              final definition = _selectedDefinition!;
              final pathQuery = _queryController.text.trim();
              const destination = null; // unsupported
              const discardPaths = null; // unsupported

              Future<OperationStarted> operation;
              switch (_entryController.entryType) {
                case DatasetEntryType.latest:
                  operation = widget.client.recoverFromLatest(
                    definition: definition,
                    pathQuery: pathQuery.isNotEmpty ? pathQuery : null,
                    destination: destination,
                    discardPaths: discardPaths,
                  );
                  break;
                case DatasetEntryType.entry:
                  operation = widget.client.recoverFrom(
                    definition: definition,
                    entry: _entryController.entry!,
                    pathQuery: pathQuery.isNotEmpty ? pathQuery : null,
                    destination: destination,
                    discardPaths: discardPaths,
                  );
                  break;
                case DatasetEntryType.until:
                  operation = widget.client.recoverUntil(
                    definition: definition,
                    until: _entryController.dateTime,
                    pathQuery: pathQuery.isNotEmpty ? pathQuery : null,
                    destination: destination,
                    discardPaths: discardPaths,
                  );
                  break;
                default:
                  throw Exception('Expected valid entry type but [${_entryController.entryType}] found');
              }

              final messenger = ScaffoldMessenger.of(context);

              operation.then((_) {
                messenger.showSnackBar(const SnackBar(content: Text('Recovery started...')));
                setState(() {});
              }).onError((e, stackTrace) {
                messenger.showSnackBar(SnackBar(content: Text('Failed to start recovery: [$e]')));
              }).whenComplete(() {
                if (context.mounted) Navigator.pop(context);
              });
            },
          ),
        );

        if (_selectedDefinition != null) {
          final entryField = datasetEntryField(
            client: widget.client,
            definition: _selectedDefinition!,
            controller: _entryController,
          );

          final queryField = TextFormField(
            decoration: const InputDecoration(labelText: 'Query'),
            controller: _queryController,
          );

          final entryCard = card(
            child: entryField,
            title: 'Data Source',
            hint:
                'Data is recovered from the latest entry, an explicitly provided entry or until a specified timestamp.',
          );

          final queryCard = card(
            child: queryField,
            title: 'File/path query to use for limiting recovery',
            hint: 'Only files and directories that match the provided (partial) query will be recovered.',
          );

          return boxed(
            context,
            child: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.start,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [definitionCard, entryCard, queryCard, submitButton],
            ),
          );
        } else {
          return boxed(
            context,
            child: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.start,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [definitionCard, submitButton],
            ),
          );
        }
      },
    );
  }

  Widget card({required Widget child, required String title, String? hint}) {
    final theme = Theme.of(context);

    final titleWidget = Text(title, style: theme.textTheme.bodySmall?.copyWith(fontStyle: FontStyle.italic));

    return Card(
      margin: const EdgeInsets.all(16.0),
      child: Column(
        children: (hint != null
                ? [
                    child,
                    titleWidget,
                    Text(
                      hint,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(fontStyle: FontStyle.italic),
                    ),
                  ]
                : [child, titleWidget])
            .map((c) => Padding(padding: const EdgeInsets.all(8.0), child: c))
            .toList(),
      ),
    );
  }
}

class RecoverButton extends StatefulWidget {
  const RecoverButton({
    super.key,
    required this.definitionController,
    required this.entryController,
    required this.onPressed,
  });

  final TextEditingController definitionController;
  final DatasetEntryController entryController;
  final void Function() onPressed;

  @override
  State createState() {
    return _RecoverButtonState();
  }
}

class _RecoverButtonState extends State<RecoverButton> {
  @override
  void initState() {
    super.initState();
    widget.definitionController.addListener(_changeListener);
    widget.entryController.addListener(_changeListener);
  }

  @override
  void dispose() {
    widget.definitionController.removeListener(_changeListener);
    widget.entryController.removeListener(_changeListener);
    super.dispose();
  }

  void _changeListener() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final definitionSelected = widget.definitionController.text.isNotEmpty;

    final entriesAvailable = widget.entryController.entryType != null;

    final entrySelected =
        widget.entryController.entryType != DatasetEntryType.entry || widget.entryController.entry != null;

    if (definitionSelected) {
      if (entriesAvailable) {
        if (entrySelected) {
          return ElevatedButton(
            onPressed: widget.onPressed,
            child: const Text('RECOVER'),
          );
        } else {
          return const ElevatedButton(
            onPressed: null,
            child: Text('SELECT AN ENTRY'),
          );
        }
      } else {
        return const ElevatedButton(
          onPressed: null,
          child: Text('RECOVER'),
        );
      }
    } else {
      return const ElevatedButton(
        onPressed: null,
        child: Text('SELECT A DEFINITION FIRST'),
      );
    }
  }
}
