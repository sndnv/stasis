import 'dart:convert';

import 'package:cryptography/cryptography.dart';

class DerivedPasswords {
  static Future<String> deriveHashedUserAuthenticationPassword({
    required String password,
    required String saltPrefix,
    required String salt,
    required int iterations,
    required int derivedKeySize,
  }) async {
    final kdf = Pbkdf2(
      macAlgorithm: Hmac.sha512(),
      iterations: iterations,
      bits: derivedKeySize * 8,
    );

    final derivedPassword = await kdf
        .deriveKey(
          secretKey: SecretKeyData(password.codeUnits),
          nonce: _authenticationSalt(saltPrefix: saltPrefix, salt: salt).codeUnits,
        )
        .then((value) => value.extractBytes());

    return base64Url.encoder.convert(derivedPassword).replaceAll('=', '');
  }

  static String _authenticationSalt({
    required String saltPrefix,
    required String salt,
  }) {
    return '$saltPrefix-authentication-$salt';
  }
}

class UserAuthenticationPasswordDerivationConfig {
  UserAuthenticationPasswordDerivationConfig({
    required this.enabled,
    required this.secretSize,
    required this.iterations,
    required this.saltPrefix,
  });

  bool enabled;
  int secretSize;
  int iterations;
  String saltPrefix;
}
