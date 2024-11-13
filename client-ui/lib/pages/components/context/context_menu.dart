import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:stasis_client_ui/pages/components/context/entry_action.dart';

class ContextMenu extends StatefulWidget {
  const ContextMenu({super.key, required this.actions, required this.child});

  final List<EntryAction> actions;
  final Widget child;

  @override
  State<StatefulWidget> createState() => _ContextMenuState();
}

class _ContextMenuState extends State<ContextMenu> {
  final MenuController _menuController = MenuController();
  bool _menuWasEnabled = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final buttons = widget.actions.map((action) {
      final icon = Icon(action.icon, color: action.color);

      final item = ListTile(
        dense: true,
        contentPadding: const EdgeInsets.symmetric(),
        visualDensity: VisualDensity.compact,
        title: Text(action.name, style: theme.textTheme.titleSmall?.copyWith(color: action.color)),
        subtitle: Text(action.description, style: theme.textTheme.bodySmall?.copyWith(color: action.color)),
      );

      return MenuItemButton(onPressed: action.handler, leadingIcon: icon, child: item);
    }).toList();

    return GestureDetector(
      onSecondaryTapDown: (d) => _toggleMenu(d.localPosition),
      child: MenuAnchor(
        controller: _menuController,
        menuChildren: buttons,
        child: widget.child,
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    _disableContextMenu();
  }

  @override
  void dispose() {
    _reEnableContextMenu();
    super.dispose();
  }

  Future<void> _disableContextMenu() async {
    if (!kIsWeb) {
      return;
    }
    _menuWasEnabled = BrowserContextMenu.enabled;
    if (_menuWasEnabled) {
      await BrowserContextMenu.disableContextMenu();
    }
  }

  void _reEnableContextMenu() {
    if (!kIsWeb) {
      return;
    }
    if (_menuWasEnabled && !BrowserContextMenu.enabled) {
      BrowserContextMenu.enableContextMenu();
    }
  }

  void _toggleMenu(Offset position) {
    _menuController.isOpen ? _menuController.close() : _menuController.open(position: position);
  }
}
