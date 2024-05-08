import 'package:flutter/material.dart';

class BackgroundProcesses extends StatefulWidget {
  const BackgroundProcesses({
    super.key,
    required this.terminationHandler,
  });

  final void Function() terminationHandler;

  @override
  State createState() {
    return _BackgroundProcessesState();
  }
}

class _BackgroundProcessesState extends State<BackgroundProcesses> {
  bool _processingResponse = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Text('An active background service was found but it is not responding', textAlign: TextAlign.center),
        _processingResponse
            ? const Center(child: CircularProgressIndicator())
            : ElevatedButton(onPressed: _terminationHandler, child: const Text('Terminate')),
      ].map((e) => Padding(padding: const EdgeInsets.all(8.0), child: e)).toList(),
    );
  }

  void _terminationHandler() async {
    setState(() {
      _processingResponse = true;
    });

    try {
      widget.terminationHandler();
    } on Exception catch (e) {
      _showSnackBar(context, message: 'Termination failed: [$e]');
    }
  }

  void _showSnackBar(BuildContext context, {required String message}) {
    final messenger = ScaffoldMessenger.of(context);
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }
}
