import 'package:flutter/cupertino.dart';

class EntryAction {
  const EntryAction({
    required this.icon,
    required this.name,
    required this.description,
    required this.handler,
    this.color,
  });

  final IconData icon;
  final String name;
  final String description;
  final void Function() handler;
  final Color? color;
}
