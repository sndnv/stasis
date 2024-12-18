import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:flutter/material.dart';

class DateTimeController extends ValueNotifier<DateTime> {
  DateTimeController({DateTime? initialValue}) : super(initialValue ?? DateTime.now());
}

class DateTimeField extends StatefulWidget {
  const DateTimeField({
    super.key,
    this.title,
    required this.controller,
  });

  final String? title;
  final DateTimeController controller;

  @override
  State createState() {
    return _DateTimeFieldState();
  }
}

class _DateTimeFieldState extends State<DateTimeField> {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final dateButton = OutlinedButton.icon(
      icon: const Icon(Icons.calendar_today),
      label: Text(widget.controller.value.toLocal().renderAsDate()),
      onPressed: () async {
        final updated = await showDatePicker(
          context: context,
          initialDate: widget.controller.value,
          firstDate: widget.controller.value.subtract(const Duration(days: 365)),
          lastDate: widget.controller.value.add(const Duration(days: 365)),
        );

        if (updated != null) {
          setState(() => widget.controller.value = _updateDate(widget.controller.value, updated));
        }
      },
    );

    final timeButton = OutlinedButton.icon(
      icon: const Icon(Icons.access_time),
      label: Text(widget.controller.value.toLocal().renderAsTime()),
      onPressed: () async {
        final updated = await showTimePicker(
          context: context,
          initialTime: TimeOfDay(hour: widget.controller.value.hour, minute: widget.controller.value.minute),
        );

        if (updated != null) {
          setState(() => widget.controller.value = _updateTime(widget.controller.value, updated));
        }
      },
    );

    final date = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: widget.title != null
          ? [
              Padding(
                padding: const EdgeInsets.only(bottom: 4.0),
                child: Text('${widget.title} On', style: theme.textTheme.bodyMedium, textAlign: TextAlign.left),
              ),
              Row(children: [Expanded(child: dateButton)]),
            ]
          : [
              Row(children: [Expanded(child: dateButton)])
            ],
    );

    final time = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: widget.title != null
          ? [
              Padding(
                padding: const EdgeInsets.only(bottom: 4.0),
                child: Text('${widget.title} At', style: theme.textTheme.bodyMedium, textAlign: TextAlign.left),
              ),
              Row(children: [Expanded(child: timeButton)]),
            ]
          : [
              Row(children: [Expanded(child: timeButton)])
            ],
    );

    return Container(
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(width: 1.38, color: theme.colorScheme.onSurface.withValues(alpha: 96))),
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
