import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/api/requests/create_user.dart';
import 'package:server_ui/model/api/responses/created_user.dart';
import 'package:server_ui/model/users/user.dart';

import '../assertions.dart';

void main() {
  group('CreateUser interop', () {
    test('decode and re-encode CreateUser', () {
      assertInterop(
        domain: 'users',
        resource: 'CreateUser',
        matches: const CreateUser(
          username: 'test',
          rawPassword: 'test-password',
          permissions: {'manage-self'},
          limits: UserLimits(
            maxDevices: 10,
            maxCrates: 100,
            maxStorage: 1099511627776,
            maxStoragePerCrate: 10737418240,
            maxRetention: Duration(seconds: 7776000),
            minRetention: Duration(seconds: 86400),
          ),
        ),
        fromJson: CreateUser.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode CreatedUser', () {
      assertInterop(
        domain: 'users',
        resource: 'CreatedUser',
        matches: const CreatedUser(user: '1528e306-7218-44c2-857c-96c3d64e29b6'),
        fromJson: CreatedUser.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
