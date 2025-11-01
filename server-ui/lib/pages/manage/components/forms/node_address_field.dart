import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:server_ui/model/nodes/node.dart';

class HttpEndpointAddressField extends StatefulWidget {
  const HttpEndpointAddressField({
    super.key,
    required this.onChange,
    this.initialValue,
  });

  final HttpEndpointAddress? initialValue;
  final void Function(HttpEndpointAddress) onChange;

  @override
  State createState() {
    return _HttpEndpointAddressFieldState();
  }
}

class _HttpEndpointAddressFieldState extends State<HttpEndpointAddressField> {
  late String? _uri = widget.initialValue?.uri;

  @override
  Widget build(BuildContext context) {
    final uriField = TextFormField(
      decoration: const InputDecoration(labelText: 'URI'),
      controller: TextEditingController(text: _uri),
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A URI is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty) {
          _uri = value;
          widget.onChange(HttpEndpointAddress(uri: _uri!));
        }
      },
    );

    return Column(children: [uriField]);
  }
}

class GrpcEndpointAddressField extends StatefulWidget {
  const GrpcEndpointAddressField({
    super.key,
    required this.onChange,
    this.initialValue,
  });

  final GrpcEndpointAddress? initialValue;
  final void Function(GrpcEndpointAddress) onChange;

  @override
  State createState() {
    return _GrpcEndpointAddressFieldState();
  }
}

class _GrpcEndpointAddressFieldState extends State<GrpcEndpointAddressField> {
  late String? _host = widget.initialValue?.host;
  late int? _port = widget.initialValue?.port;
  late bool _tlsEnabled = widget.initialValue?.tlsEnabled ?? true;

  @override
  Widget build(BuildContext context) {
    final hostField = TextFormField(
      decoration: const InputDecoration(labelText: 'Host'),
      controller: TextEditingController(text: _host),
      validator: (value) {
        if (value == null || value.isEmpty) {
          return 'A host is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        if (value.isNotEmpty) {
          _host = value;
          if (_addressAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    final portField = TextFormField(
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      decoration: const InputDecoration(labelText: 'Port'),
      controller: TextEditingController(text: _port?.toString()),
      validator: (value) {
        final actualValue = int.tryParse(value ?? '');
        if (actualValue == null || actualValue <= 0 || actualValue > 65535) {
          return 'A valid port is required';
        } else {
          return null;
        }
      },
      onChanged: (value) {
        final actualValue = int.tryParse(value);
        if (actualValue != null) {
          _port = actualValue;
          if (_addressAvailable()) {
            widget.onChange(_fromFields());
          }
        }
      },
    );

    final tlsEnabledField = ListTile(
      title: const Text('TLS Enabled'),
      trailing: Switch(
        value: _tlsEnabled,
        activeThumbColor: Theme.of(context).colorScheme.primary,
        onChanged: (value) {
          setState(() => _tlsEnabled = value);
          if (_addressAvailable()) {
            widget.onChange(_fromFields());
          }
        },
      ),
    );

    return Column(children: [hostField, portField, tlsEnabledField]);
  }

  bool _addressAvailable() {
    return _host != null && _port != null;
  }

  GrpcEndpointAddress _fromFields() {
    return GrpcEndpointAddress(
      host: _host!,
      port: _port!,
      tlsEnabled: _tlsEnabled,
    );
  }
}
