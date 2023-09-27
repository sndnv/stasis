import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/operations/operation.dart' as operation;
import 'package:stasis_client_ui/model/operations/operation_progress.dart';
import 'package:stasis_client_ui/model/operations/operation_state.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/operation_summary.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/triple.dart';
import 'package:flutter/material.dart';

class OperationDetails extends StatefulWidget {
  const OperationDetails({
    super.key,
    required this.operation,
    required this.client,
  });

  final OperationProgress operation;
  final ClientApi client;

  @override
  State createState() {
    return _OperationDetailsState();
  }
}

class _OperationDetailsState extends State<OperationDetails> {
  @override
  Widget build(BuildContext context) {
    return StreamBuilder(
      stream: widget.client.followOperation(operation: widget.operation.operation),
      builder: (context, snapshot) {
        final theme = Theme.of(context);
        final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
        final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);
        final smallError = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error);

        Widget summary;
        List<Widget> details;

        void onOperationStateChange({required bool removed}) {
          if (removed) {
            Navigator.pop(context);
          } else {
            setState(() {});
          }
        }

        if (snapshot.data != null &&
            (snapshot.connectionState == ConnectionState.active || snapshot.connectionState == ConnectionState.done)) {
          Widget? metadata;
          Widget? failures;
          Widget? stages;

          switch (snapshot.data.runtimeType.toString().replaceAll('_\$_', '')) {
            case 'BackupState':
              final state = snapshot.data as BackupState;

              summary = OperationSummary.build(context,
                  operation: OperationProgress(
                    operation: widget.operation.operation,
                    isActive: widget.operation.isActive,
                    type: operation.Type.backup,
                    progress: state.asProgress(),
                  ),
                  client: widget.client,
                  onChange: onOperationStateChange);

              metadata = ListTile(
                title: Text('Metadata', style: mediumBold),
                subtitle: RichText(
                  text: TextSpan(
                    children: [
                      TextSpan(text: ' Collected: ', style: theme.textTheme.bodySmall),
                      TextSpan(text: state.metadataCollected?.render() ?? '-', style: smallBold),
                      TextSpan(text: '\n Pushed: ', style: theme.textTheme.bodySmall),
                      TextSpan(text: state.metadataPushed?.render() ?? '-', style: smallBold),
                    ],
                  ),
                ),
              );

              final errorMessages = state.entities.unmatched +
                  state.failures +
                  state.entities.failed.entries.map((e) => '[${e.key}] - ${e.value}').toList();

              final stageValues = {
                'discovered': state.entities.discovered.map((e) => Triple(e, 1, 1)).toList(),
                'examined': state.entities.examined.map((e) => Triple(e, 1, 1)).toList(),
                'collected': state.entities.collected.map((e) => Triple(e, 1, 1)).toList(),
                'pending': state.entities.pending.entries.map((e) {
                  return Triple(e.key, e.value.processedParts, e.value.expectedParts);
                }).toList(),
                'processed': state.entities.processed.entries.map((e) {
                  return Triple(e.key, e.value.processedParts, e.value.expectedParts);
                }).toList()
              };

              failures = _renderFailures(mediumBold, smallError, errorMessages);
              stages = _renderStages(mediumBold, theme.textTheme.bodySmall, smallBold, stageValues);

              break;

            case 'RecoveryState':
              final state = snapshot.data as RecoveryState;

              summary = OperationSummary.build(
                context,
                operation: OperationProgress(
                  operation: widget.operation.operation,
                  isActive: widget.operation.isActive,
                  type: operation.Type.recovery,
                  progress: state.asProgress(),
                ),
                client: widget.client,
                onChange: onOperationStateChange,
              );

              final errorMessages =
                  state.failures + state.entities.failed.entries.map((e) => '[${e.key}] - ${e.value}').toList();

              final stageValues = {
                'examined': state.entities.examined.map((e) => Triple(e, 1, 1)).toList(),
                'collected': state.entities.collected.map((e) => Triple(e, 1, 1)).toList(),
                'pending': state.entities.pending.entries.map((e) {
                  return Triple(e.key, e.value.processedParts, e.value.expectedParts);
                }).toList(),
                'processed': state.entities.processed.entries.map((e) {
                  return Triple(e.key, e.value.processedParts, e.value.expectedParts);
                }).toList(),
                'metadata-applied': state.entities.metadataApplied.map((e) => Triple(e, 1, 1)).toList()
              };

              failures = _renderFailures(mediumBold, smallError, errorMessages);
              stages = _renderStages(mediumBold, theme.textTheme.bodySmall, smallBold, stageValues);

              break;

            default:
              summary = OperationSummary.build(
                context,
                operation: widget.operation,
                client: widget.client,
                onChange: onOperationStateChange,
              );
              break;
          }

          details = (metadata != null ? [metadata] : <Widget>[]) +
              (failures != null ? [failures] : []) +
              (stages != null ? [stages] : []);
        } else if (snapshot.error is BadRequest) {
          summary = OperationSummary.build(
            context,
            operation: widget.operation,
            client: widget.client,
            onChange: onOperationStateChange,
          );

          details = [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text('No additional information available', style: theme.textTheme.bodySmall),
            ),
          ];
        } else if (snapshot.error != null) {
          summary = OperationSummary.build(
            context,
            operation: widget.operation,
            client: widget.client,
            onChange: onOperationStateChange,
          );

          details = [
            createBasicCard(
              theme,
              [errorInfo(title: 'Error', description: snapshot.error.toString())],
            ),
          ];
        } else {
          summary = OperationSummary.build(
            context,
            operation: widget.operation,
            client: widget.client,
            onChange: onOperationStateChange,
          );

          details = [const Center(child: CircularProgressIndicator())];
        }

        const divider = Padding(padding: EdgeInsets.symmetric(horizontal: 8.0), child: Divider());

        return SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[summary, divider] + details,
          ),
        );
      },
    );
  }

  Widget? _renderFailures(TextStyle? titleStyle, TextStyle? subtitleStyle, List<String> errors) {
    if (errors.isNotEmpty) {
      return ListTile(
        title: Text('Errors', style: titleStyle),
        subtitle: Text(errors.map((e) => ' $e').join('\n'), style: subtitleStyle),
      );
    } else {
      return null;
    }
  }

  Widget? _renderStages(
    TextStyle? titleStyle,
    TextStyle? textStyle,
    TextStyle? highlightStyle,
    Map<String, List<Triple<String, int, int>>> stages,
  ) {
    if (stages.isNotEmpty) {
      const density = VisualDensity(
        horizontal: VisualDensity.minimumDensity,
        vertical: VisualDensity.minimumDensity,
      );

      final list = ListView(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        children: ListTile.divideTiles(
          context: context,
          tiles: stages.entries.map((e) {
            return ListTile(
              dense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 4.0),
              visualDensity: density,
              title: RichText(
                text: TextSpan(
                  children: [
                    TextSpan(text: e.key.toOperationStageString(), style: highlightStyle),
                    TextSpan(text: ' (steps: ', style: textStyle),
                    TextSpan(text: e.value.length.toString(), style: highlightStyle),
                    TextSpan(text: ')', style: textStyle),
                  ],
                ),
              ),
              onTap: () {
                showDialog(
                  context: context,
                  builder: (_) => SimpleDialog(
                    title: Text(e.key.toOperationStageString()),
                    children: e.value.isNotEmpty
                        ? e.value.map(
                            (e) {
                              final split = e.a.toSplitPath();

                              return ListTile(
                                dense: true,
                                visualDensity: density,
                                title: (e.c > 1)
                                    ? RichText(
                                        text: TextSpan(
                                          children: [
                                            TextSpan(text: split.b, style: highlightStyle),
                                            TextSpan(text: ' (${e.b} of ${e.c})', style: textStyle),
                                          ],
                                        ),
                                      )
                                    : Text(split.b, style: highlightStyle),
                                subtitle: Text(split.a, style: textStyle),
                              );
                            },
                          ).toList()
                        : [const Center(child: Text('No Data'))],
                  ),
                );
              },
            );
          }),
        ).toList(),
      );

      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
        child: Column(
          mainAxisSize: MainAxisSize.max,
          mainAxisAlignment: MainAxisAlignment.start,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Stages', style: titleStyle),
            list,
          ],
        ),
      );
    } else {
      return null;
    }
  }
}
