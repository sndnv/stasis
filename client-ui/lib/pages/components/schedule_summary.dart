import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/model/schedules/schedule.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/pages/components/schedule_assignment_summary.dart';
import 'package:flutter/material.dart';

class ScheduleSummary {
  static ExpansionTile build(
    BuildContext context, {
    required String scheduleId,
    required Schedule? schedule,
    required List<ActiveSchedule> assigned,
  }) {
    final theme = Theme.of(context);
    final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
    final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);
    final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);

    final assignmentSummaries = assigned.isNotEmpty
        ? assigned.map((e) => ScheduleAssignmentSummary.build(context, schedule: e)).toList()
        : [
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 16.0),
              child: Text('No Assignments', style: mediumItalic),
            )
          ];

    if (schedule == null) {
      final title = RichText(
        text: TextSpan(
          children: [
            TextSpan(text: scheduleId.toMinimizedString(), style: mediumBold),
          ],
        ),
      );

      return ExpansionTile(
        title: title,
        children: assignmentSummaries,
      );
    } else {
      final nextInvocation = schedule.nextInvocation;
      final active = assigned.map((s) => s.assignment.toAssignmentTypeString()).toSet().toList();

      final title = RichText(
        text: TextSpan(
          children: [
            TextSpan(text: schedule.info, style: mediumBold),
          ],
        ),
      );

      final subtitle = Padding(
        padding: const EdgeInsets.all(4.0),
        child: RichText(
          text: TextSpan(
            children: [
                  TextSpan(text: 'Next run on ', style: theme.textTheme.bodySmall),
                  TextSpan(text: nextInvocation.renderAsDate(), style: smallBold),
                  TextSpan(text: ' at ', style: theme.textTheme.bodySmall),
                  TextSpan(text: nextInvocation.renderAsTime(), style: smallBold),
                  TextSpan(text: '\nRunning every ', style: theme.textTheme.bodySmall),
                  TextSpan(text: schedule.interval.render(), style: smallBold),
                ] +
                (active.isEmpty
                    ? [
                        TextSpan(text: '\nNo active assignments', style: smallBold),
                      ]
                    : [
                        TextSpan(text: '\nActive assignments: ', style: theme.textTheme.bodySmall),
                        TextSpan(text: active.join(', '), style: smallBold),
                      ]),
          ),
        ),
      );

      return ExpansionTile(title: title, subtitle: subtitle, children: assignmentSummaries);
    }
  }
}
