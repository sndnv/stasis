import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/operations/operation.dart' as operation;
import 'package:stasis_client_ui/model/operations/operation_progress.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/operation_details.dart';
import 'package:stasis_client_ui/pages/components/operation_summary.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:flutter/material.dart';

class Operations extends StatefulWidget {
  const Operations({
    super.key,
    required this.client,
  });

  final ClientApi client;

  @override
  State createState() {
    return _OperationsState();
  }
}

class _OperationsState extends State<Operations> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<OperationProgress>>(
      of: () => widget.client.getOperations(state: operation.State.all),
      builder: (context, operations) {
        final theme = Theme.of(context);

        final operationsList = ListView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: (operations
                ..sort(
                  (a, b) => b.progress.started.compareTo(a.progress.started),
                ))
              .map((o) {
            return OperationSummary.build(
              context,
              operation: o,
              client: widget.client,
              onChange: ({required bool removed}) => setState(() {}),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (context) => Scaffold(
                      appBar: TopBar.fromTitle(context, 'Operation details'),
                      body: OperationDetails(operation: o, client: widget.client),
                    ),
                    fullscreenDialog: true,
                  ),
                ).then((_) => setState(() {}));
              },
            );
          }).toList(),
        );

        final noOperations = Padding(
          padding: const EdgeInsets.all(16.0),
          child: Center(
            child: Text(
              'No operations',
              style: theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
            ),
          ),
        );

        return boxed(
          context,
          child: Card(
            margin: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.max,
              mainAxisAlignment: MainAxisAlignment.start,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [operations.isNotEmpty ? operationsList : noOperations],
            ),
          ),
        );
      },
    );
  }
}
