import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/model/nodes/crate_store_descriptor.dart';
import 'package:server_ui/model/nodes/node.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';
import 'package:server_ui/pages/page_router.dart';

extension ExtendedString on String {
  Widget withCopyButton() {
    return Row(
      children: [Text(this), _copyButton(text: this)],
    );
  }

  String toMinimizedString() => split('-').last;

  Widget withLink(Link link) {
    return Tooltip(
      message: 'Show in ${link.destination.title}',
      child: RichText(
        text: TextSpan(
          text: this,
          style: link.theme.textTheme.bodyLarge?.copyWith(color: link.theme.primaryColor),
          recognizer: TapGestureRecognizer()..onTap = link.navigate,
        ),
      ),
    );
  }

  Widget asShortId({Link? link}) {
    final shortenedId = toMinimizedString();

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        link == null ? Text(shortenedId) : shortenedId.withLink(link),
        _copyButton(text: this),
      ],
    );
  }

  Widget withInfo(void Function() onPressed) {
    return Row(
      children: [
        Text(this),
        IconButton(
          tooltip: 'Show more...',
          onPressed: onPressed,
          icon: const Icon(Icons.info_outline),
        )
      ],
    );
  }

  Widget hiddenWithInfo(void Function() onPressed, {Icon? icon}) {
    return Row(
      children: [
        RichText(
          text: const TextSpan(
            text: 'hidden',
            style: TextStyle(fontStyle: FontStyle.italic),
          ),
        ),
        IconButton(
          tooltip: 'Show more...',
          onPressed: onPressed,
          icon: icon ?? const Icon(Icons.info_outline),
        )
      ],
    );
  }
}

extension ExtendedWidget on Widget {
  Widget withCopyButton({required String copyText}) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [this, _copyButton(text: copyText)],
    );
  }
}

extension ExtendedNode on Node {
  Widget renderAddress(BuildContext context) {
    switch (actualType()) {
      case LocalNode:
        final descriptor = (this as LocalNode).storeDescriptor;
        return descriptor.location().withInfo(
          () {
            List<Widget> descriptorInfo;

            switch (descriptor.actualType()) {
              case StreamingMemoryBackendDescriptor:
                final memory = (descriptor as StreamingMemoryBackendDescriptor);
                descriptorInfo = [
                  ListTile(
                    title: const Text('Type'),
                    leading: const Icon(Icons.device_hub),
                    trailing: Text(memory.backendType),
                  ),
                  ListTile(
                    title: const Text('Name'),
                    leading: const Icon(Icons.info_outline),
                    trailing: Text(memory.name),
                  ),
                  ListTile(
                    title: const Text('Max Size'),
                    leading: const Icon(Icons.sd_storage),
                    trailing: Text(memory.maxSize.renderFileSize()),
                  ),
                  ListTile(
                    title: const Text('Max Chunk Size'),
                    leading: const Icon(Icons.sd_storage),
                    trailing: Text(memory.maxChunkSize.renderFileSize()),
                  ),
                ];
                break;
              case ContainerBackendDescriptor:
                final container = (descriptor as ContainerBackendDescriptor);
                descriptorInfo = [
                  ListTile(
                    title: const Text('Type'),
                    leading: const Icon(Icons.device_hub),
                    trailing: Text(container.backendType),
                  ),
                  ListTile(
                    title: const Text('Path'),
                    leading: const Icon(Icons.folder),
                    trailing: Text(container.path),
                  ),
                  ListTile(
                    title: const Text('Max Chunks'),
                    leading: const Icon(Icons.sd_storage),
                    trailing: Text(container.maxChunks.renderNumber()),
                  ),
                  ListTile(
                    title: const Text('Max Chunk Size'),
                    leading: const Icon(Icons.sd_storage),
                    trailing: Text(container.maxChunkSize.renderFileSize()),
                  ),
                ];
                break;
              case FileBackendDescriptor:
                final file = (descriptor as FileBackendDescriptor);
                descriptorInfo = [
                  ListTile(
                    title: const Text('Type'),
                    leading: const Icon(Icons.device_hub),
                    trailing: Text(file.backendType),
                  ),
                  ListTile(
                    title: const Text('Directory'),
                    leading: const Icon(Icons.folder),
                    trailing: Text(file.parentDirectory),
                  ),
                ];
                break;
              default:
                throw ArgumentError('Unexpected descriptor type encountered: [$descriptor.runtimeType]');
            }

            showDialog(
              context: context,
              builder: (_) => SimpleDialog(
                title: Text('Info for [${id().toMinimizedString()}]'),
                children: descriptorInfo
                    .map((e) => ConstrainedBox(constraints: const BoxConstraints(minWidth: 384.0), child: e))
                    .toList(),
              ),
            );
          },
        );
      case RemoteHttpNode:
        return Text((this as RemoteHttpNode).address.uri);
      case RemoteGrpcNode:
        final address = (this as RemoteGrpcNode).address;
        return Row(
          children: [
            Text('${address.host}:${address.port.toString()}'),
            Tooltip(
              message: 'TLS ${address.tlsEnabled ? 'enabled' : 'disabled'}',
              child: address.tlsEnabled
                  ? const Icon(Icons.lock, color: Colors.green, size: 16.0)
                  : const Icon(Icons.lock_open, color: Colors.deepOrange, size: 16.0),
            ),
          ],
        );
      default:
        throw ArgumentError('Unexpected node type encountered: [$runtimeType]');
    }
  }
}

class Link {
  Link({required this.buildContext, required this.destination, required this.withFilter}) {
    theme = Theme.of(buildContext);
  }

  void navigate() {
    PageRouter.navigateTo(
      buildContext,
      destination: destination,
      withFilter: withFilter,
    );
  }

  BuildContext buildContext;
  PageRouterDestination destination;
  String withFilter;
  late ThemeData theme;
}

Widget _copyButton({required String text}) {
  return IconButton(
    splashRadius: 16.0,
    iconSize: 16.0,
    tooltip: 'Copy',
    onPressed: () async => await Clipboard.setData(ClipboardData(text: text)),
    icon: const Icon(Icons.copy),
  );
}
