import 'package:stasis_client_ui/model/datasets/dataset_metadata.dart';
import 'package:stasis_client_ui/model/operations/operation.dart';
import 'package:stasis_client_ui/model/operations/operation_state.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

extension ExtendedString on String {
  Widget withCopyButton() {
    return Row(
      children: [Text(this), _copyButton(text: this)],
    );
  }

  String toMinimizedString() => split('-').last;

  Widget asShortId() {
    final shortenedId = toMinimizedString();

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(shortenedId),
        _copyButton(text: this),
      ],
    );
  }

  Widget withInfo(void Function() onPressed) {
    return Row(
      children: [
        Text(this),
        IconButton(
          tooltip: 'Show more...',
          onPressed: onPressed,
          icon: const Icon(Icons.info_outline),
        )
      ],
    );
  }

  Widget hiddenWithInfo(void Function() onPressed, {Icon? icon}) {
    return Row(
      children: [
        RichText(
          text: const TextSpan(
            text: 'hidden',
            style: TextStyle(fontStyle: FontStyle.italic),
          ),
        ),
        IconButton(
          tooltip: 'Show more...',
          onPressed: onPressed,
          icon: icon ?? const Icon(Icons.info_outline),
        )
      ],
    );
  }
}

extension ExtendedWidget on Widget {
  Widget withCopyButton({required String copyText}) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [this, _copyButton(text: copyText)],
    );
  }
}

Widget _copyButton({required String text}) {
  return IconButton(
    splashRadius: 16.0,
    iconSize: 16.0,
    tooltip: 'Copy',
    onPressed: () async => await Clipboard.setData(ClipboardData(text: text)),
    icon: const Icon(Icons.copy),
  );
}

extension ExtendedEntityState on EntityState {
  Widget entityIcon() {
    return entityState.entityIcon();
  }
}

extension ExtendedEntityStateString on String {
  Widget entityIcon() {
    const delay = Duration(milliseconds: 500);

    switch (this) {
      case 'existing':
        return const Tooltip(
          message: 'Existing file or directory that has not changed',
          waitDuration: delay,
          child: Icon(Icons.playlist_add_check),
        );
      case 'updated':
        return const Tooltip(
          message: 'Existing file or directory that has changed',
          waitDuration: delay,
          child: Icon(Icons.edit_note),
        );
      default:
        return const Tooltip(message: 'New file or directory', waitDuration: delay, child: Icon(Icons.playlist_add));
    }
  }
}

extension ExtendedOperationState on OperationState {
  Progress asProgress() {
    if (this is BackupState) {
      final state = this as BackupState;
      return Progress(
        started: state.started,
        total: state.entities.discovered.length,
        processed: state.entities.skipped.length + state.entities.processed.length,
        failures: state.entities.failed.length + state.failures.length,
        completed: state.completed,
      );
    } else if (this is RecoveryState) {
      final state = this as RecoveryState;
      return Progress(
        started: state.started,
        total: state.entities.examined.length,
        processed: state.entities.processed.length,
        failures: state.entities.failed.length + state.failures.length,
        completed: state.completed,
      );
    } else {
      throw ArgumentError('Unexpected operation state encountered: [$runtimeType]');
    }
  }
}
