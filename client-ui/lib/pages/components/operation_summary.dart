import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/operations/operation.dart' as op;
import 'package:stasis_client_ui/model/operations/operation_progress.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:flutter/material.dart';

class OperationSummary {
  static ListTile build(
    BuildContext context, {
    required OperationProgress operation,
    required ClientApi client,
    required void Function({required bool removed}) onChange,
    void Function()? onTap,
  }) {
    final theme = Theme.of(context);
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
    final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);
    final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);
    final smallError = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error);
    final smallErrorBold = theme.textTheme.bodySmall?.copyWith(
      fontWeight: FontWeight.bold,
      color: theme.colorScheme.error,
    );

    final title = RichText(
      text: TextSpan(
        children: [
          TextSpan(text: operation.type.render(), style: mediumBold),
          TextSpan(text: ' (', style: theme.textTheme.bodyMedium),
          TextSpan(text: operation.operation.toMinimizedString(), style: mediumItalic),
          TextSpan(text: ')', style: theme.textTheme.bodyMedium),
        ],
      ),
    );

    final subtitle = Padding(
      padding: const EdgeInsets.all(4.0),
      child: RichText(
        text: TextSpan(
          children: [
                TextSpan(text: 'Started: ', style: theme.textTheme.bodyMedium),
                TextSpan(text: operation.progress.started.render(), style: mediumBold),
              ] +
              [
                TextSpan(text: '\nProcessed: ', style: theme.textTheme.bodySmall),
                TextSpan(text: operation.progress.processed.toString(), style: smallBold),
                TextSpan(text: ' of ', style: theme.textTheme.bodySmall),
                TextSpan(text: operation.progress.total.toString(), style: smallBold),
              ] +
              (operation.progress.failures > 0
                  ? [
                      TextSpan(text: ', Errors: ', style: smallError),
                      TextSpan(text: operation.progress.failures.toString(), style: smallErrorBold),
                    ]
                  : []) +
              (operation.progress.completed != null
                  ? [
                      TextSpan(text: '\nCompleted: ', style: theme.textTheme.bodySmall),
                      TextSpan(text: operation.progress.completed?.render(), style: smallBold),
                    ]
                  : [
                      TextSpan(text: '\nCompleted: ', style: theme.textTheme.bodySmall),
                      TextSpan(
                        text: operation.progress.total > 0
                            ? '${(operation.progress.processed / operation.progress.total * 100).toStringAsFixed(1)}%'
                            : '0.0%',
                        style: smallBold,
                      ),
                    ]),
        ),
      ),
    );

    final isCompleted = operation.progress.completed != null;

    final OutlinedButton? resumeButton = operation.type == op.Type.backup && !isCompleted && !operation.isActive
        ? OutlinedButton(
            onPressed: () {
              confirmationDialog(
                context,
                title: 'Resume operation [${operation.operation.toMinimizedString()}]?',
                onConfirm: () {
                  final messenger = ScaffoldMessenger.of(context);
                  client.resumeOperation(operation: operation.operation).then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Operation resumed...')));
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to resume operation: [$e]')));
                  }).whenComplete(() => onChange(removed: false));
                },
              );
            },
            child: const Text('RESUME'),
          )
        : null;

    final stopButton = !isCompleted && operation.isActive
        ? OutlinedButton(
            onPressed: () {
              confirmationDialog(
                context,
                title: 'Stop operation [${operation.operation.toMinimizedString()}]?',
                onConfirm: () {
                  final messenger = ScaffoldMessenger.of(context);
                  client.stopOperation(operation: operation.operation).then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Operation stopped...')));
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to stop operation: [$e]')));
                  }).whenComplete(() => onChange(removed: false));
                },
              );
            },
            child: const Text('STOP'),
          )
        : null;

    final removeButton = !operation.isActive
        ? OutlinedButton(
            onPressed: () {
              confirmationDialog(
                context,
                title: 'Remove operation [${operation.operation.toMinimizedString()}]?',
                onConfirm: () {
                  final messenger = ScaffoldMessenger.of(context);
                  client.removeOperation(operation: operation.operation).then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Operation removed...')));
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to remove operation: [$e]')));
                  }).whenComplete(() => onChange(removed: true));
                },
              );
            },
            child: const Icon(Icons.close),
          )
        : null;

    return ListTile(
      title: title,
      leading: operation.isActive ? const CircularProgressIndicator() : null,
      subtitle: subtitle,
      trailing: Wrap(
        children: [resumeButton, stopButton, removeButton]
            .nonNulls
            .map((e) => Padding(padding: const EdgeInsets.all(2.0), child: e))
            .toList(),
      ),
      onTap: onTap,
    );
  }
}
