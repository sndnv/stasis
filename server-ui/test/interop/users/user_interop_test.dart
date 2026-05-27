import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/users/user.dart';

import '../assertions.dart';

void main() {
  final user = User(
    id: '7a1c27c2-0b3c-4f23-a68b-814084bfee7d',
    salt: 'test-salt-value',
    active: true,
    permissions: const {'view-self'},
    limits: const UserLimits(
      maxDevices: 10,
      maxCrates: 100,
      maxStorage: 1099511627776,
      maxStoragePerCrate: 10737418240,
      maxRetention: Duration(seconds: 7776000),
      minRetention: Duration(seconds: 86400),
    ),
    created: DateTime.parse('2026-03-01T12:00:00Z'),
    updated: DateTime.parse('2026-03-02T13:00:00Z'),
  );

  final userWithoutLimits = user.copyWith(limits: null);

  group('User interop', () {
    test('decode and re-encode a user', () {
      assertInterop(
        domain: 'users',
        resource: 'User',
        matches: user,
        fromJson: User.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a user with no limits', () {
      assertInterop(
        domain: 'users',
        resource: 'User.no-limits',
        matches: userWithoutLimits,
        fromJson: User.fromJson,
        toJson: (value) => value.toJson(),
      );
    });

    test('decode and re-encode a user with null limits', () {
      assertInterop(
        domain: 'users',
        resource: 'User.null-limits',
        matches: userWithoutLimits,
        fromJson: User.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
