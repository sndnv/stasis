import 'dart:io';

import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/utils/debouncer.dart';

class ClientLogs extends StatefulWidget {
  const ClientLogs({
    super.key,
    required this.stdout,
    this.stderr = const [],
  });

  final List<String> stdout;
  final List<String> stderr;

  @override
  State createState() {
    return _ClientLogsState();
  }

  static Future<List<String>> loadLogsFromFile({required String? path}) async {
    if (path != null) {
      final sanitized = path.endsWith(Platform.pathSeparator) ? path.substring(0, path.length - 1) : path;
      return File('$sanitized/stasis-client.log').readAsLines();
    } else {
      return [];
    }
  }

  static String? getLogsDir() {
    final home = Platform.environment['HOME'];
    return home != null ? '$home/stasis-client/logs' : null;
  }
}

class _ClientLogsState extends State<ClientLogs> {
  late String _selectedLogs = widget.stdout.isNotEmpty ? 'stdout' : 'stderr';
  bool _hideNoise = true;
  String _filterText = '';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final sourceButtons = Visibility(
      visible: widget.stderr.isNotEmpty,
      child: SegmentedButton<String>(
        segments: [
          ButtonSegment<String>(
            value: 'stdout',
            icon: Icon(Icons.terminal),
            label: Text('stdout'),
          ),
          ButtonSegment<String>(
            value: 'stderr',
            icon: Icon(Icons.error_outline),
            label: Text('stderr'),
          ),
        ],
        selected: {_selectedLogs},
        onSelectionChanged: (selected) {
          setState(() => _selectedLogs = selected.first);
        },
      ),
    );

    final searchDebouncer = Debouncer(timeout: Duration(milliseconds: 500));

    final searchInput = TextField(
      textAlign: TextAlign.center,
      decoration: InputDecoration(
        border: OutlineInputBorder(),
        hint: Text('Filter logs', textAlign: TextAlign.center),
        isDense: true,
        suffixIcon: Tooltip(
          message: '${_hideNoise ? 'Show' : 'Hide'} logs that are usually not interesting or relevant',
          child: IconButton(
            onPressed: () => setState(() => _hideNoise = !_hideNoise),
            icon: Icon(_hideNoise ? Icons.filter_alt_off : Icons.filter_alt),
          ),
        ),
      ),
      onChanged: (value) {
        searchDebouncer.run(() {
          setState(() => _filterText = value.trim());
        });
      },
    );

    final List<String> content;
    switch (_selectedLogs) {
      case 'stderr':
        content = widget.stderr;
      default:
        content = widget.stdout;
    }

    final filteredNoise = _hideNoise
        ? content.whereNot(
            (line) => line.contains('ch.qos.logback') || line.contains('c.q.l') || line.trim().isEmpty,
          )
        : content;

    final filteredContent = _filterText.isNotEmpty
        ? filteredNoise.where((line) => line.toLowerCase().contains(_filterText.toLowerCase()))
        : filteredNoise;

    final Widget controls = Row(
      mainAxisSize: MainAxisSize.max,
      children: [
        Flexible(flex: 3, fit: FlexFit.tight, child: sourceButtons),
        Spacer(flex: 2),
        Flexible(flex: 3, fit: FlexFit.tight, child: searchInput),
      ],
    );

    final Widget count = Padding(
      padding: EdgeInsetsGeometry.symmetric(vertical: 8.0),
      child: Tooltip(
        message:
            '${content.length - filteredNoise.length} logs hidden due to not being interesting or relevant; '
            '${filteredNoise.length - filteredContent.length} logs hidden because of custom filter',
        child: Text(
          'Showing ${filteredContent.length} of ${content.length} logs',
          style: theme.textTheme.labelSmall?.copyWith(fontStyle: FontStyle.italic),
        ),
      ),
    );

    final defaultStyle = theme.textTheme.bodyMedium;
    final debugStyle = defaultStyle?.copyWith(color: theme.colorScheme.tertiary);
    final warningStyle = defaultStyle?.copyWith(color: theme.colorScheme.secondary);
    final errorStyle = defaultStyle?.copyWith(color: theme.colorScheme.error);

    TextStyle? styleFromLog(String log) {
      if (log.contains('[INFO')) {
        return defaultStyle;
      } else if (log.contains('[WARN')) {
        return warningStyle;
      } else if (log.contains('[ERROR')) {
        return errorStyle;
      } else if (log.contains('[DEBUG')) {
        return debugStyle;
      } else {
        return defaultStyle;
      }
    }

    final List<Widget> logs = filteredContent
        .map(
          (line) => Row(
            children: [
              Flexible(
                flex: 1,
                child: Container(
                  margin: EdgeInsetsGeometry.symmetric(vertical: 2.0),
                  padding: EdgeInsetsGeometry.only(left: 8.0),
                  decoration: BoxDecoration(
                    border: Border(left: BorderSide(color: Colors.grey)),
                  ),
                  child: Text(line, style: styleFromLog(line)),
                ),
              ),
            ],
          ),
        )
        .toList();

    return SizedBox(
      width: MediaQuery.of(context).size.width,
      child: Column(
        children: [
          controls,
          count,
          SelectionArea(child: Column(children: logs)),
        ],
      ),
    );
  }
}
