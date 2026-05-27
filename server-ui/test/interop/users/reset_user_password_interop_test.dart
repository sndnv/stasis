import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/update_user_password.dart';

import '../assertions.dart';

void main() {
  group('ResetUserPassword interop', () {
    test('decode and re-encode ResetUserPassword', () {
      assertInterop(
        domain: 'users',
        resource: 'ResetUserPassword',
        matches: const UpdateUserPassword(rawPassword: 'new-password-value'),
        fromJson: UpdateUserPassword.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
