import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/model/users/permission.dart';

void main() {
  group('User Permissions should', () {
    test('support creation from strings', () async {
      expect(UserPermission.fromName('view-self'), UserPermission.viewSelf);
      expect(UserPermission.fromName('view-privileged'), UserPermission.viewPrivileged);
      expect(UserPermission.fromName('view-public'), UserPermission.viewPublic);
      expect(UserPermission.fromName('view-service'), UserPermission.viewService);
      expect(UserPermission.fromName('manage-self'), UserPermission.manageSelf);
      expect(UserPermission.fromName('manage-privileged'), UserPermission.managePrivileged);
      expect(UserPermission.fromName('manage-service'), UserPermission.manageService);

      expect(() => UserPermission.fromName('other'), throwsArgumentError);
    });
  });
}
