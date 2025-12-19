import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_bootstrap_form.dart';

class ClientNotConfiguredCard extends StatefulWidget {
  const ClientNotConfiguredCard({
    super.key,
    required this.applicationName,
    required this.processes,
    required this.e,
    required this.bootstrapCallback,
  });

  final String applicationName;
  final AppProcesses processes;
  final ConfigFileNotAvailableException e;

  final void Function(bool isSuccessful) bootstrapCallback;

  @override
  State createState() {
    return _ClientNotConfiguredCardState();
  }
}

class _ClientNotConfiguredCardState extends State<ClientNotConfiguredCard> {
  bool _showBootstrap = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (_showBootstrap) {
      return createBasicCard(
        theme,
        [
          ClientBootstrapForm(
            processes: widget.processes,
            callback: (isSuccessful) {
              if (isSuccessful) {
                setState(() => _showBootstrap = false);
              }
              widget.bootstrapCallback(isSuccessful);
            },
            onCancelled: () => setState(() => _showBootstrap = false),
          ),
        ],
      );
    } else {
      return createBasicCard(
        theme,
        [
          Text('Client Not Configured', style: theme.textTheme.headlineSmall),
          RichText(
            text: TextSpan(
              children: [
                TextSpan(text: 'Configuration ', style: theme.textTheme.bodyMedium),
                WidgetSpan(
                  alignment: PlaceholderAlignment.middle,
                  child: Tooltip(
                    message: widget.e.path,
                    child: Text(
                      'file',
                      style: theme.textTheme.bodyMedium?.copyWith(decoration: TextDecoration.underline),
                    ),
                  ),
                ),
                TextSpan(text: ' is missing or inaccessible', style: theme.textTheme.bodyMedium),
              ],
            ),
          ),
          const Divider(),
          Text(
            'If you are running the client for the first time, the device bootstrap process needs to be completed',
            style: theme.textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
          ElevatedButton(
            onPressed: () => setState(() => _showBootstrap = true),
            child: Text('Start Bootstrap'),
          ),
          const Divider(),
          Text(
            'The bootstrap process can also be done via the CLI:',
            style: theme.textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
          SelectionArea(
            child: Text(
              '${widget.applicationName}-cli bootstrap',
              style: theme.textTheme.labelSmall?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      );
    }
  }
}
