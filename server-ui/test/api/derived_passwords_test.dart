import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/api/derived_passwords.dart';

void main() {
  group('DerivedPasswords should', () {
    test('support deriving hashed authentication passwords', () async {
      const expected = '1owhNJEee-Av51YxBXblvBoTOyHD7KN9';

      final actual = await DerivedPasswords.deriveHashedUserAuthenticationPassword(
        password: 'test-password',
        saltPrefix: 'test-prefix',
        salt: 'test-salt',
        iterations: 100000,
        derivedKeySize: 24,
      );

      expect(actual, expected);
    });
  });
}
