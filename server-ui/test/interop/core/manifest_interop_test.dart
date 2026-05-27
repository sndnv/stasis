import 'package:flutter_test/flutter_test.dart';
import 'package:server_ui/model/manifests/manifest.dart';

import '../assertions.dart';

void main() {
  group('Manifest interop', () {
    test('decode and re-encode a manifest', () {
      assertInterop(
        domain: 'core',
        resource: 'Manifest',
        matches: Manifest(
          crate: 'daa9dd9e-828d-4810-b34f-736d3b742aad',
          size: 8388608,
          copies: 3,
          origin: 'c4db10f8-a72c-456f-9f6b-b9ef650e1f3f',
          source: '71cb1be7-140d-4a85-b9b1-a3559e11f73f',
          destinations: const [
            '8c4efe24-1c2d-44de-a437-40f22e1a9aac',
            '341bb614-d228-4cbc-a4cf-ac5afaa1e50b',
          ],
          created: DateTime.parse('2026-02-10T14:20:00Z'),
        ),
        fromJson: Manifest.fromJson,
        toJson: (value) => value.toJson(),
      );
    });
  });
}
