import 'dart:io';

import 'package:stasis_client_ui/api/endpoint_context.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const certificatePath = './test/resources/localhost.p12';
  const certificatePassword = '';

  group('A DefaultHttpsContext should', () {
    test('providing a security context', () async {
      final context = DefaultHttpsContext();
      expect(context.get(), SecurityContext.defaultContext);

      // the test certificate should NOT already be loaded so this operation should succeed
      expect(
        () => context.get().setTrustedCertificates(certificatePath, password: certificatePassword),
        returnsNormally,
      );
    });
  });

  group('A CustomHttpsContext should', () {
    test('providing a security context', () async {
      final context = CustomHttpsContext(certificatePath: certificatePath, certificatePassword: certificatePassword);
      expect(context.get(), isNot(SecurityContext.defaultContext));

      // the test certificate should already be loaded so this operation should fail
      try {
        context.get().setTrustedCertificates(certificatePath, password: certificatePassword);
      } on TlsException catch (e) {
        expect(e.message, 'Failure trusting builtin roots');
        expect(e.osError?.message.trim(), 'CERT_ALREADY_IN_HASH_TABLE(x509_lu.c:357)');
      }
    });
  });
}
