import 'package:stasis_client_ui/config/api_token.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('A ApiTokenFactory should', () {
    test('load API tokens from files', () async {
      const tokenFile = './test/resources/api_token';

      final token = ApiTokenFactory.load(path: tokenFile);

      expect(token, 'test-token');
    });

    test('fail to load API tokens from missing files', () async {
      const tokenFile = './test/resources/missing_api_token';

      expect(ApiTokenFactory.load(path: tokenFile), null);
    });
  });
}
