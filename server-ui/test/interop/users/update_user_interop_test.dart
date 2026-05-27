import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/update_user_limits.dart';
import 'package:server_ui/model/api/requests/update_user_permissions.dart';
import 'package:server_ui/model/api/requests/update_user_salt.dart';
import 'package:server_ui/model/api/requests/update_user_state.dart';
import 'package:server_ui/model/api/responses/updated_user_salt.dart';
import 'package:server_ui/model/users/user.dart';

import '../assertions.dart';

void main() {
  group('UpdateUser interop', () {
    test('decode and re-encode UpdateUserLimits', () {
      assertInterop(
        domain: 'users',
        resource: 'UpdateUserLimits',
        matches: const UpdateUserLimits(
          limits: UserLimits(
            maxDevices: 20,
            maxCrates: 200,
            maxStorage: 2199023255552,
            maxStoragePerCrate: 21474836480,
            maxRetention: Duration(seconds: 15552000),
            minRetention: Duration(seconds: 172800),
          ),
        ),
        fromJson: UpdateUserLimits.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateUserPermissions', () {
      assertInterop(
        domain: 'users',
        resource: 'UpdateUserPermissions',
        matches: const UpdateUserPermissions(permissions: {'manage-self'}),
        fromJson: UpdateUserPermissions.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateUserSalt', () {
      assertInterop(
        domain: 'users',
        resource: 'UpdateUserSalt',
        matches: const UpdateUserSalt(salt: 'new-salt-value'),
        fromJson: UpdateUserSalt.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdateUserState', () {
      assertInterop(
        domain: 'users',
        resource: 'UpdateUserState',
        matches: const UpdateUserState(active: true),
        fromJson: UpdateUserState.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode UpdatedUserSalt', () {
      assertInterop(
        domain: 'users',
        resource: 'UpdatedUserSalt',
        matches: const UpdatedUserSalt(salt: 'new-salt-value'),
        fromJson: UpdatedUserSalt.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
