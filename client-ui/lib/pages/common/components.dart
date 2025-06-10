import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/pages/components/sizing.dart';
import 'package:stasis_client_ui/utils/env.dart';

SvgPicture createLogo({double size = 48.0}) {
  return SvgPicture.asset(
    'assets/logo.svg',
    width: size,
    height: size,
    fit: BoxFit.contain,
  );
}

Card createBasicCard(ThemeData theme, List<Widget> children) {
  return Card(
    color: theme.colorScheme.surfaceContainerHighest,
    child: Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: children.map((e) => Padding(padding: const EdgeInsets.all(4.0), child: e)).toList(),
          ),
        )
      ],
    ),
  );
}

Widget buildPage<T>({
  required Future<T> Function() of,
  required Widget Function(BuildContext context, T data) builder,
}) {
  return FutureBuilder<T>(
    future: of().timeout(Duration(seconds: getConfiguredTimeout())),
    builder: (context, snapshot) {
      final theme = Theme.of(context);

      if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
        return builder(context, snapshot.data as T);
      } else if (snapshot.error != null) {
        Widget error;

        switch (snapshot.error.runtimeType) {
          case const (ApiUnavailable):
            error = errorInfo(
              title: 'Background Service Unavailable',
              description: 'Failed to connect to background service.',
            );
            break;
          case const (AuthenticationFailure):
            error = errorInfo(
              title: 'Authentication Failure',
              description:
                  'Background service request was rejected by the server.\n\nCheck the logs for more information.',
            );
            break;
          case const (TimeoutException):
            error = errorInfo(
              title: 'Request Timeout',
              description: 'Background service took too long to respond.',
            );
            break;
          default:
            error = errorInfo(
              title: 'Error',
              description: snapshot.error.toString(),
            );
            break;
        }

        return centerContent(content: [
          createBasicCard(theme, [error])
        ]);
      } else {
        return const Center(child: CircularProgressIndicator());
      }
    },
  );
}

Widget centerContent({required List<Widget> content}) {
  return Scaffold(
    body: Center(
      child: Container(
        constraints: const BoxConstraints(minWidth: 400, maxWidth: 400),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: content,
        ),
      ),
    ),
  );
}

Widget errorInfo({required String title, required String description}) {
  return Center(
    child: ListTile(
      leading: const Icon(Icons.error),
      title: Text(title),
      subtitle: Text(description),
    ),
  );
}

void confirmationDialog(
  BuildContext context, {
  required String title,
  required void Function() onConfirm,
  Widget? content,
}) {
  showDialog(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: Text(title),
        content: content,
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('CANCEL'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(context);
              onConfirm();
            },
            child: const Text('OK'),
          ),
        ],
      );
    },
  );
}

void showFileContentDialog(
  BuildContext context, {
  required String name,
  required String parentDirectory,
  required Widget content,
}) {
  showDialog(
    context: context,
    builder: (context) {
      final theme = Theme.of(context);

      return SimpleDialog(
        title: SelectionArea(
          child: Column(
            mainAxisSize: MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Tooltip(
                message: 'Config file name',
                child: Text(name),
              ),
              Tooltip(
                message: 'Config file parent directory',
                child: Text(parentDirectory, style: theme.textTheme.bodySmall),
              ),
            ],
          ),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SingleChildScrollView(
                  scrollDirection: Axis.vertical,
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SelectionArea(
                      child: DecoratedBox(
                        decoration: BoxDecoration(color: theme.canvasColor),
                        child: content,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          )
        ],
      );
    },
  );
}

Widget infoSection({
  required Widget content,
  required Color color,
  EdgeInsetsGeometry? padding,
}) {
  return Padding(
    padding: padding ?? const EdgeInsets.all(8.0),
    child: Container(
      decoration: BoxDecoration(
        border: Border(left: BorderSide(color: color, width: 3.0)),
      ),
      child: Padding(
        padding: const EdgeInsets.only(left: 8.0, top: 4.0, bottom: 4.0),
        child: content,
      ),
    ),
  );
}

Widget boxed(BuildContext context, {required Widget child}) {
  final media = MediaQuery.of(context);

  double widthFactor;

  if (media.size.width > Sizing.sm && media.size.width <= Sizing.lg) {
    widthFactor = 0.6;
  } else if (media.size.width > Sizing.lg && media.size.width <= Sizing.xl) {
    widthFactor = 0.5;
  } else if (media.size.width > Sizing.xl) {
    widthFactor = 0.4;
  } else {
    widthFactor = 1.0;
  }

  return SingleChildScrollView(
    child: Center(
      child: FractionallySizedBox(
        alignment: Alignment.topCenter,
        widthFactor: widthFactor,
        child: child,
      ),
    ),
  );
}
