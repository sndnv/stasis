import 'package:flutter_test/flutter_test.dart';
import 'package:stasis_client_ui/model/users/user.dart';

void main() {
  group('An ExtendedUser should', () {
    final user = User(
      id: 'test-id',
      active: true,
      permissions: {},
      created: DateTime.parse('2020-10-01T01:03:01'),
      updated: DateTime.parse('2020-10-01T01:03:02'),
    );

    test('check if permissions allow for privileged view access', () async {
      expect(user.viewPrivilegedAllowed(), false);
      expect(user.copyWith(permissions: {'view-privileged'}).viewPrivilegedAllowed(), true);
    });

    test('check if permissions allow for service view access', () async {
      expect(user.viewServiceAllowed(), false);
      expect(user.copyWith(permissions: {'view-service'}).viewServiceAllowed(), true);
    });

    test('check if permissions allow for privileged management access', () async {
      expect(user.managePrivilegedAllowed(), false);
      expect(user.copyWith(permissions: {'manage-privileged'}).managePrivilegedAllowed(), true);
    });

    test('check if permissions allow for service management access', () async {
      expect(user.manageServiceAllowed(), false);
      expect(user.copyWith(permissions: {'manage-service'}).manageServiceAllowed(), true);
    });
  });
}
