import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:flutter/material.dart';

class ScheduleAssignmentSummary {
  static ListTile build(
    BuildContext context, {
    required ActiveSchedule schedule,
  }) {
    final title = _assignmentTitle(context, schedule.assignment);
    final subtitle = _assignmentDetails(schedule.assignment);

    return ListTile(
      title: title,
      subtitle: subtitle,
      visualDensity: VisualDensity.compact,
    );
  }

  static Widget _assignmentTitle(BuildContext context, Assignment assignment) {
    final theme = Theme.of(context);
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);

    switch (assignment.runtimeType.toString().replaceAll('_\$_', '')) {
      case 'BackupAssignment':
        return RichText(
          text: TextSpan(
            children: [
              TextSpan(text: 'Backup for (', style: theme.textTheme.bodyMedium),
              TextSpan(text: (assignment as BackupAssignment).definition.toMinimizedString(), style: mediumBold),
              TextSpan(text: ')', style: theme.textTheme.bodyMedium),
            ],
          ),
        );
      case 'ExpirationAssignment':
        return Text('Expiration', style: theme.textTheme.bodyMedium);
      case 'ValidationAssignment':
        return Text('Validation', style: theme.textTheme.bodyMedium);
      case 'KeyRotationAssignment':
        return Text('Key Rotation', style: theme.textTheme.bodyMedium);
      default:
        throw ArgumentError('Unexpected assignment type encountered: [${assignment.runtimeType}]');
    }
  }

  static Widget? _assignmentDetails(Assignment assignment) {
    switch (assignment.runtimeType.toString().replaceAll('_\$_', '')) {
      case 'BackupAssignment':
        final entities = (assignment as BackupAssignment).entities;
        if (entities.isNotEmpty) {
          return Text(entities.join(', '));
        } else {
          return const Text('*');
        }
      default:
        return null;
    }
  }
}
