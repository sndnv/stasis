import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/model/schedules/schedule.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/pages/components/schedule_summary.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:stasis_client_ui/utils/triple.dart';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';

class Schedules extends StatelessWidget {
  const Schedules({
    super.key,
    required this.client,
    required this.files,
  });

  final ClientApi client;
  final AppFiles files;

  @override
  Widget build(BuildContext context) {
    return buildPage<Pair<List<Schedule>, List<ActiveSchedule>>>(
      of: () => client
          .getPublicSchedules()
          .then((public) => client.getConfiguredSchedules().then((configured) => Pair(public, configured))),
      builder: (context, schedules) {
        final theme = Theme.of(context);

        final public = schedules.a;
        final configured = schedules.b;

        final assigned = configured
            .groupListsBy((c) => c.assignment.schedule())
            .entries
            .map((e) => Triple(e.key, public.firstWhereOrNull((p) => p.id == e.key), e.value))
            .toList();

        final assignedScheduleIds = assigned.map((e) => e.a).toSet();

        final unassigned = public
            .whereNot((p) => assignedScheduleIds.contains(p.id))
            .map((p) => Triple(p.id, p, <ActiveSchedule>[]))
            .toList();

        final now = DateTime.now();

        final entries = (assigned + unassigned)..sortBy((e) => e.b?.nextInvocation ?? now);

        final schedulesList = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: entries.map((e) {
            return ScheduleSummary.build(
              context,
              scheduleId: e.a,
              schedule: e.b,
              assigned: e.c,
            );
          }).toList(),
        );

        final noSchedules = Padding(
          padding: const EdgeInsets.all(16.0),
          child: Center(
            child: Text(
              'No schedules',
              style: theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
            ),
          ),
        );

        final configFile = files.paths.schedules.toSplitPath();

        final showConfigFile = FloatingActionButton.small(
          heroTag: null,
          onPressed: () {
            showFileContentDialog(
              context,
              name: configFile.b,
              parentDirectory: configFile.a,
              content: Text(files.schedules.isNotEmpty ? files.schedules.join('\n') : 'none'),
            );
          },
          tooltip: 'Show config file',
          child: const Icon(Icons.file_open_outlined),
        );

        return Stack(
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: boxed(
                context,
                child: Card(
                  margin: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.max,
                    mainAxisAlignment: MainAxisAlignment.start,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [entries.isNotEmpty ? schedulesList : noSchedules],
                  ),
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomRight,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: showConfigFile,
              ),
            ),
          ],
        );
      },
    );
  }
}
