import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/api/requests/create_schedule.dart';
import 'package:server_ui/model/api/requests/update_schedule.dart';
import 'package:server_ui/model/schedules/schedule.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_form.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';

class Schedules extends StatefulWidget {
  const Schedules({
    super.key,
    required this.client,
    required this.privileged,
  });

  final SchedulesApiClient client;
  final bool privileged;

  @override
  State createState() {
    return _SchedulesState();
  }
}

class _SchedulesState extends State<Schedules> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<Schedule>>(
      of: () => widget.privileged ? widget.client.getSchedules() : widget.client.getPublicSchedules(),
      builder: (context, schedules) {
        return EntityTable<Schedule>(
          entities: schedules,
          actions: widget.privileged
              ? [
                  IconButton(
                    tooltip: 'Create New Schedule',
                    onPressed: () => _createSchedule(context),
                    icon: const Icon(Icons.add),
                  ),
                ]
              : [],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final schedule = entity as Schedule;
            return schedule.id.contains(filter) || schedule.info.contains(filter);
          },
          header: const Text('Schedules'),
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Info', sortBy: (e) => e.info),
            EntityTableColumn(label: 'Public', sortBy: (e) => e.isPublic.toString()),
            EntityTableColumn(label: 'Start', sortBy: (e) => e.start),
            EntityTableColumn(label: 'Interval', sortBy: (e) => e.interval),
            EntityTableColumn(label: 'Next Invocation', sortBy: (e) => (e as Schedule).nextInvocation()),
            EntityTableColumn(label: ''),
          ],
          entityToRow: (entity) {
            final schedule = entity as Schedule;

            return [
              DataCell(schedule.id.asShortId()),
              DataCell(Text(schedule.info)),
              DataCell(Text(schedule.isPublic ? 'Yes' : 'No')),
              DataCell(Text(schedule.start.render())),
              DataCell(Text(schedule.interval.render())),
              DataCell(Text(schedule.nextInvocation().render())),
              DataCell(
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: widget.privileged
                      ? [
                          IconButton(
                            tooltip: 'Update Schedule',
                            onPressed: () => _updateSchedule(context, schedule),
                            icon: const Icon(Icons.edit),
                          ),
                          IconButton(
                            tooltip: 'Remove Schedule',
                            onPressed: () => _removeSchedule(context, schedule.id),
                            icon: const Icon(Icons.delete),
                          ),
                        ]
                      : [
                          const IconButton(
                            tooltip: 'No actions available',
                            onPressed: null,
                            icon: Icon(Icons.do_not_disturb),
                          )
                        ],
                ),
              ),
            ];
          },
        );
      },
    );
  }

  void _createSchedule(BuildContext context) async {
    final infoField = formField(
      title: 'Info',
      errorMessage: 'Info cannot be empty',
      controller: TextEditingController(),
    );

    DateTime start = DateTime.now();
    final startField = dateTimeField(
      title: 'Start',
      onChange: (updated) => start = updated,
    );

    Duration? interval;
    final intervalField = durationField(
      title: 'Interval',
      onChange: (updated) => interval = updated,
      errorMessage: 'A schedule interval is required',
    );

    bool isPublic = false;
    final publicField = booleanField(
      title: 'Public',
      initialValue: isPublic,
      onChange: (updated) => isPublic = updated,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) {
        return SimpleDialog(
          title: const Text('Create New Schedule'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [infoField, startField, intervalField, publicField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final request = CreateSchedule(
                  info: infoField.controller!.text.trim(),
                  isPublic: isPublic,
                  start: start,
                  interval: interval!,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.createSchedule(request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Schedule created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create schedule: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _updateSchedule(BuildContext context, Schedule existing) async {
    final infoField = formField(
      title: 'Info',
      errorMessage: 'Info cannot be empty',
      controller: TextEditingController(text: existing.info),
    );

    DateTime start = existing.start;
    final startField = dateTimeField(
      title: 'Start',
      onChange: (updated) => start = updated,
      initialDateTime: start,
    );

    Duration interval = existing.interval;
    final intervalField = durationField(
      title: 'Interval',
      onChange: (updated) => interval = updated,
      errorMessage: 'A schedule interval is required',
      initialDuration: interval,
    );

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) {
        return SimpleDialog(
          title: Text('Update Schedule [${existing.id.toMinimizedString()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [infoField, startField, intervalField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateSchedule(
                  info: infoField.controller!.text.trim(),
                  start: start,
                  interval: interval,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.updateSchedule(id: existing.id, request: request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Schedule updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update schedule: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _removeSchedule(BuildContext context, String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove schedule [${id.toMinimizedString()}]?'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.deleteSchedule(id: id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Schedule removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove schedule: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
              child: const Text('Remove'),
            ),
          ],
        );
      },
    );
  }
}
