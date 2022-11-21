import 'package:flutter/material.dart';
import 'package:multi_select_flutter/multi_select_flutter.dart';
import 'package:server_ui/model/users/permission.dart';

class UserPermissionsField extends StatefulWidget {
  const UserPermissionsField({
    super.key,
    required this.title,
    required this.onChange,
    this.initialPermissions,
    this.errorMessage,
  });

  final String title;
  final List<UserPermission>? initialPermissions;
  final void Function(List<UserPermission>) onChange;
  final String? errorMessage;

  @override
  State createState() {
    return _UserPermissionsFieldState();
  }
}

class _UserPermissionsFieldState extends State<UserPermissionsField> {
  final _permissions = [
    UserPermission.viewSelf,
    UserPermission.viewPrivileged,
    UserPermission.viewPublic,
    UserPermission.viewService,
    UserPermission.manageSelf,
    UserPermission.managePrivileged,
    UserPermission.manageService,
  ]..sort((a, b) => a.name.compareTo(b.name));

  late List<UserPermission> _selectedPermissions = widget.initialPermissions ?? [];

  @override
  Widget build(BuildContext context) {
    final permissionsInput = MultiSelectDialogField(
      title: Text(widget.title),
      buttonText: Text(widget.title),
      items: _permissions.map((e) => MultiSelectItem(e, e.name)).toList(),
      initialValue: _selectedPermissions,
      listType: MultiSelectListType.CHIP,
      dialogWidth: MediaQuery.of(context).size.width * 0.25,
      onConfirm: (values) {
        _selectedPermissions = values;
        widget.onChange(_selectedPermissions);
      },
      validator: (values) {
        if (values == null || values.isEmpty) {
          return widget.errorMessage;
        } else {
          return null;
        }
      },
    );

    return permissionsInput;
  }
}
