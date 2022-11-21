import 'package:flutter/material.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';

class DateTimeField extends StatefulWidget {
  const DateTimeField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialDateTime,
  });

  final String title;
  final DateTime? initialDateTime;
  final void Function(DateTime) onChange;

  @override
  State createState() {
    return _DateTimeFieldState();
  }
}

class _DateTimeFieldState extends State<DateTimeField> {
  late DateTime _dateTime = widget.initialDateTime ?? DateTime.now();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final dateButton = OutlinedButton.icon(
      icon: const Icon(Icons.calendar_today),
      label: Text(_dateTime.toLocal().renderAsDate()),
      onPressed: () async {
        final updated = await showDatePicker(
          context: context,
          initialDate: _dateTime,
          firstDate: _dateTime.subtract(const Duration(days: 365)),
          lastDate: _dateTime.add(const Duration(days: 365)),
        );

        if (updated != null) {
          setState(() => _dateTime = _updateDate(_dateTime, updated));
          widget.onChange(_dateTime);
        }
      },
    );

    final timeButton = OutlinedButton.icon(
      icon: const Icon(Icons.access_time),
      label: Text(_dateTime.toLocal().renderAsTime()),
      onPressed: () async {
        final updated = await showTimePicker(
          context: context,
          initialTime: TimeOfDay(hour: _dateTime.hour, minute: _dateTime.minute),
        );

        if (updated != null) {
          setState(() => _dateTime = _updateTime(_dateTime, updated));
          widget.onChange(_dateTime);
        }
      },
    );

    final date = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 4.0),
          child: Text('${widget.title} On', style: theme.textTheme.bodyMedium, textAlign: TextAlign.left),
        ),
        Row(children: [Expanded(child: dateButton)]),
      ],
    );

    final time = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 4.0),
          child: Text('${widget.title} At', style: theme.textTheme.bodyMedium, textAlign: TextAlign.left),
        ),
        Row(children: [Expanded(child: timeButton)]),
      ],
    );

    return Container(
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(width: 1.38, color: theme.colorScheme.onSurface.withOpacity(0.38))),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8.0),
        child: Row(
          children: [
            Expanded(flex: 5, child: date),
            const Padding(padding: EdgeInsets.symmetric(horizontal: 4.0)),
            Expanded(flex: 2, child: time),
          ],
        ),
      ),
    );
  }

  DateTime _updateDate(DateTime original, DateTime updated) {
    return DateTime(updated.year, updated.month, updated.day, original.hour, original.minute);
  }

  DateTime _updateTime(DateTime original, TimeOfDay updated) {
    return DateTime(original.year, original.month, original.day, updated.hour, updated.minute);
  }
}
