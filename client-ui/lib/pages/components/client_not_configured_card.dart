import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:flutter/material.dart';

class ClientNotConfiguredCard {
  static Widget build(BuildContext context, String applicationName, ConfigFileNotAvailableException e) {
    final theme = Theme.of(context);

    return createBasicCard(
      theme,
      [
        Text('Client Not Configured', style: theme.textTheme.headlineSmall),
        RichText(
          text: TextSpan(
            children: [
              TextSpan(text: 'Configuration ', style: theme.textTheme.bodyMedium),
              WidgetSpan(
                child: Tooltip(
                  message: e.path,
                  child: Text(
                    'file',
                    style: theme.textTheme.bodyMedium?.copyWith(decoration: TextDecoration.underline),
                  ),
                ),
              ),
              TextSpan(text: ' is missing or inaccessible', style: theme.textTheme.bodyMedium)
            ],
          ),
        ),
        const Divider(),
        Text(
          'If you are running the client for the first time, the bootstrap process should be completed via the CLI:',
          style: theme.textTheme.bodySmall,
          textAlign: TextAlign.center,
        ),
        SelectionArea(
          child: Text(
            '$applicationName bootstrap',
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ],
    );
  }
}
